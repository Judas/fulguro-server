package com.fulgurogo.league.ogs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.league.LeagueModule.TAG
import com.fulgurogo.league.ogs.model.OgsLeagueMatch
import com.fulgurogo.league.ogs.model.OgsLeagueMatchRequest
import com.fulgurogo.league.ogs.model.OgsLeagueMemberRequest
import com.fulgurogo.ogs.api.OgsApiClient
import com.google.gson.Gson

/**
 * The three calls the server makes to the OGS online-league API, and nothing else.
 *
 * There is deliberately no `findMatch`: `POST /matches/` is idempotent on `league_match_id` — 201 on creation, 200
 * afterwards, same id and same invitation links — so resuming an interrupted creation is just replaying the call. That
 * removes the read-before-write this was first designed with, and halves a draw's OGS traffic.
 *
 * The fourth call of the contract, `PUT /callback`, is **not** here. It is passed by hand, once, from production: the
 * template is global to the league, and the league is shared with dev, so a registration from a developer's machine
 * would repoint production's callbacks at an unreachable localhost. See `doc/migration ligue.sql`.
 *
 * Its own [OgsApiClient] instance, like `OgsService` and `OgsRealTimeService` have theirs, because `ensureSpamDelay` is
 * instance state. Sharing one would give a real global rate guarantee but would change the behaviour of code already in
 * production — `OgsService` ticks every 15s and would start waiting on the league's calls.
 *
 * ⚠ The consequence is worth knowing: three instances are three counters, so nothing guarantees 500ms between a league
 * call and an `OgsService` call. That is already true between the two existing services, so the league does not
 * introduce the problem — it makes it a third worse.
 *
 * Every method answers null or false on failure rather than throwing. A draw creates N matches in a row, and one refused
 * by OGS must cost that one match, not the whole tick: the row stays in the database with no links, and the next tick
 * replays the call.
 */
class OgsLeagueClient(private val client: OgsApiClient = OgsApiClient()) {
    private val gson: Gson = Gson()

    /**
     * Registers a member, or updates the starting rating of one already registered. True when OGS knows them.
     *
     * 201 and 200 both mean success and are treated the same — the distinction is evidence of idempotence, not something
     * to branch on. Which is also why losing our `ogs_registered` flag is harmless: replaying the call has no effect.
     */
    fun registerMember(memberId: String): Boolean = try {
        val body = gson.toJson(OgsLeagueMemberRequest(LEAGUE_START_RATING))
        val response = client.put("$baseUrl/member/$memberId", body, headers())
        log(TAG, "registerMember $memberId OK $response")
        true
    } catch (e: Exception) {
        log(TAG, "registerMember $memberId FAILURE ${e.message}", e)
        false
    }

    /**
     * Creates the challenge for a pairing, or returns the existing one when [leagueMatchId] has already been used.
     *
     * ⚠ [leagueMatchId] is the **only** key of that idempotence: two calls carrying the same id with different players
     * return the first match, answering 200 as though all were well. So the id has to be unique per what it actually
     * designates — which is why it is prefixed with the database name, dev and prod sharing one league.
     */
    fun createMatch(blackMemberId: String, whiteMemberId: String, leagueMatchId: String): OgsLeagueMatch? = try {
        val request = OgsLeagueMatchRequest(
            blackMemberId = blackMemberId,
            whiteMemberId = whiteMemberId,
            leagueMatchId = leagueMatchId,
            rules = RULES,
            handicap = HANDICAP,
            height = BOARD_SIZE,
            width = BOARD_SIZE,
            timeControl = TIME_CONTROL,
            mainTime = MAIN_TIME_SECONDS,
            periods = PERIODS,
            periodTime = PERIOD_TIME_SECONDS
        )
        val response = client.post("$baseUrl/matches/", gson.toJson(request), headers())
        val match = gson.fromJson(response, OgsLeagueMatch::class.java)
        // The invitation links are secrets, so the id is logged and the body is not.
        log(TAG, "createMatch $leagueMatchId OK ogsMatchId=${match?.id}")
        match
    } catch (e: Exception) {
        log(TAG, "createMatch $leagueMatchId FAILURE ${e.message}", e)
        null
    }

    /** The state and result of a match, for the catch-up that runs when no callback arrived. */
    fun matchStatus(ogsMatchId: Int): OgsLeagueMatch? = try {
        val response = client.get("$baseUrl/matches/$ogsMatchId", headers())
        gson.fromJson(response, OgsLeagueMatch::class.java)
    } catch (e: Exception) {
        log(TAG, "matchStatus $ogsMatchId FAILURE ${e.message}", e)
        null
    }

    /**
     * The two organiser headers, on every request. Without them these endpoints answer 403 — which is also what keeps
     * the invitation links from being harvested by a third party.
     */
    private fun headers(): Map<String, String> = mapOf(
        "X-OGS-LEAGUE" to Config.get("ogs.league.id"),
        "X-OGS-LEAGUE-AUTH" to Config.get("ogs.league.auth")
    )

    /** `ogs.api.url` is the shared base; the online-league surface hangs off `/online_league`. */
    private val baseUrl: String get() = "${Config.get("ogs.api.url")}/online_league"

    companion object {
        /**
         * What we send as a member's starting rating. One constant for everybody, and we entrust it with nothing.
         *
         * The field is required by `PUT /member/{id}`, but OGS keeps its own league ranking from the games played, and
         * our `gold_ratings.rating` serves the **draw**, on our side. Measured: the rating sent stays in
         * `pending_rating_change` and `league_rating` remains null until an OGS account is linked.
         */
        const val LEAGUE_START_RATING = 1500

        /**
         * The game settings. Not preferences — each value is what it is because another one breaks something else, and a
         * league game is meant to score in every existing count.
         *
         * - [BOARD_SIZE] 19: `fgc_validity_games` keeps only 19×19, so a 13×13 would drop league games from the FGC count.
         * - [RULES] japanese: komi 7.5 by default, which makes a drawn score impossible and leaves no hole in the renown
         *   scale, and it sits inside the `komi > 6 AND komi < 9` window FGC accepts.
         * - [HANDICAP] 0: FGC requires it, and the houses credit their `even_game` bonus on it. The draw already balances
         *   by rating; `-1` would make the games fairer and drop them from both counts.
         * - [TIME_CONTROL] byoyomi: the system `isLongGame()` knows how to read from `main_time`.
         * - [MAIN_TIME_SECONDS] 2400: two thresholds of existing code, neither negotiable. `isLongGame()` wants
         *   `main_time >= 1200` for the houses' `long_game` bonus, **and** `speed == "live"` — `OgsService` drops
         *   correspondence games at ingestion and the WebSocket only ever sees live, so a correspondence league would be
         *   invisible to the whole pipeline.
         * - [PERIODS] and [PERIOD_TIME_SECONDS] 5 × 30s: `periods` is mandatory as soon as the control is byoyomi, and
         *   its absence is a 400, measured.
         */
        const val BOARD_SIZE = 19
        const val RULES = "japanese"
        const val HANDICAP = 0
        const val TIME_CONTROL = "byoyomi"
        const val MAIN_TIME_SECONDS = 2400
        const val PERIODS = 5
        const val PERIOD_TIME_SECONDS = 30
    }
}
