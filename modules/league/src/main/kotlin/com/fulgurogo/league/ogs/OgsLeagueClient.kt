package com.fulgurogo.league.ogs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.league.LeagueModule.TAG
import com.fulgurogo.league.ogs.model.OgsLeagueMatch
import com.fulgurogo.league.ogs.model.OgsLeagueMatchPage
import com.fulgurogo.league.ogs.model.OgsLeagueMatchRequest
import com.fulgurogo.league.ogs.model.OgsLeagueMemberRequest
import com.fulgurogo.ogs.api.OgsApiClient
import com.google.gson.Gson

/**
 * The calls the server makes to the OGS online-league API, and nothing else.
 *
 * There is deliberately no `findMatch`: `POST /matches/` is idempotent on `league_match_id` — 201 on creation, 200
 * afterwards, same id and same invitation links — so resuming an interrupted creation is just replaying the call. That
 * removes the read-before-write this was first designed with, and halves a draw's OGS traffic.
 *
 * There is no end-of-game callback either, and `PUT /callback` is nowhere in the project. It carried no data — a bare
 * GET on a URL of ours with a match id — so it could only ever have triggered the very call [sessionMatches] already
 * makes. Step 8 of `doc/plan-ligue.md` argues it at length.
 *
 * Its own [OgsApiClient] instance, **cookie-free**, and both halves matter. `ensureSpamDelay` is instance state, so a
 * shared one would make `OgsService` wait on the league's calls. And the cookies are worse than useless here: the
 * WebSocket service logs into OGS with a real account, its `sessionid` lands in the shared jar, and a league write
 * carrying it is refused with `403 CSRF Failed` — measured, 200 before that login and 403 after. Sharing one would give a real global rate guarantee but would change the behaviour of code already in
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
class OgsLeagueClient(private val client: OgsApiClient = OgsApiClient(sendCookies = false)) {
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
     * [leagueMatchId] has to be unique per what it actually designates, which is why it is prefixed with the database
     * name, dev and prod sharing one league. Two environments sending the **same** id with the same players, session and
     * colours would otherwise share one challenge and its links without either noticing — the one case OGS cannot catch,
     * since the payloads are identical.
     *
     * [season] and [sessionNumber] are here only to name the match, which is what players see on OGS.
     *
     * ⚠ **OGS compares every field it is sent against the stored match, not just [leagueMatchId].** A replay whose name,
     * handicap, board size, rules, time or players differ answers 400 and names the offending field. Two things follow,
     * and the second is the one that bites:
     *
     * - Replaying an interrupted creation is safe **because this payload is deterministic** — the settings are constants
     *   and the name is derived from the season and the session. Nothing here may become time- or state-dependent.
     * - The settings of a match are therefore **frozen for its lifetime**. Changing a constant below, or the format of
     *   [matchName], mid-season would not merely apply to new matches: it would make every replay touching an already
     *   created match fail with a 400, which is exactly the path a draw uses to recover.
     */
    fun createMatch(
        blackMemberId: String,
        whiteMemberId: String,
        leagueMatchId: String,
        season: String,
        sessionNumber: Int
    ): OgsLeagueMatch? = try {
        val request = OgsLeagueMatchRequest(
            blackMemberId = blackMemberId,
            whiteMemberId = whiteMemberId,
            leagueMatchId = leagueMatchId,
            name = matchName(season, sessionNumber),
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

    /**
     * One match by its OGS id. Kept for diagnosing a single match by hand; the sweep uses [sessionMatches].
     */
    fun matchStatus(ogsMatchId: Int): OgsLeagueMatch? = try {
        val response = client.get("$baseUrl/matches/$ogsMatchId", headers())
        gson.fromJson(response, OgsLeagueMatch::class.java)
    } catch (e: Exception) {
        log(TAG, "matchStatus $ogsMatchId FAILURE ${e.message}", e)
        null
    }

    /**
     * Every match whose `league_match_id` starts with [prefix], with its full status — this is how results arrive.
     *
     * One request instead of one per match, which is what makes a callback pointless. Our ids being
     * `<db.name>_<season>_<session>_<black>`, a prefix of `fg_prod_2026-2027_8_` returns exactly one session, of one
     * season, of one environment: bounded whatever the league's history, and a dev run sees only its own matches.
     *
     * `__startswith` is honoured, and — the part that makes this safe to build on — an unknown field name is a **400**,
     * not a filter silently ignored. Measured; see `doc/ogs-online-league-api.md`.
     *
     * Pagination follows `next` and never increments a page number, because a page past the last answers 400 rather than
     * an empty page. [MAX_PAGES] is a backstop against a server that always returns a `next`, not an expected limit: at
     * [PAGE_SIZE] per page, one session fits in the first page many times over, which is precisely why the loop has to be
     * right the first time — nothing will exercise it.
     *
     * An empty list on failure, like the rest of this client. A sweep that comes back empty writes nothing, and the next
     * tick tries again.
     */
    fun sessionMatches(prefix: String): List<OgsLeagueMatch> = try {
        val matches = mutableListOf<OgsLeagueMatch>()
        var url: String? = "$baseUrl/matches/?league_match_id__startswith=$prefix&page_size=$PAGE_SIZE"
        var pages = 0

        while (url != null && pages < MAX_PAGES) {
            val page = gson.fromJson(client.get(url, headers()), OgsLeagueMatchPage::class.java)
            matches += page.results
            url = page.next
            pages++
        }
        if (url != null) log(TAG, "sessionMatches $prefix STOPPED after $MAX_PAGES pages, ${matches.size} matches")

        matches
    } catch (e: Exception) {
        log(TAG, "sessionMatches $prefix FAILURE ${e.message}", e)
        listOf()
    }

    /**
     * The name of the match, and therefore of the game: `Ligue d'Aurak — Saison 2026 - 2027 — Session 08`.
     *
     * French, because unlike every other field of the payload this one is read by players, on OGS and in their game list.
     * The season is spaced out from the `2026-2027` the code works with, and the session is padded to two digits so the
     * names of one season all have the same shape and sort in order.
     *
     * 47 characters against the 255 the spec allows, so there is no truncation to worry about — worth stating, because a
     * name over the limit would be a 400 on every single creation of a draw rather than a cosmetic problem.
     */
    private fun matchName(season: String, sessionNumber: Int): String =
        "Ligue d'Aurak — Saison ${season.replace("-", " - ")} — Session ${sessionNumber.toString().padStart(2, '0')}"

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

        /** Comfortably more than a session holds, so [OgsLeagueClient.sessionMatches] is one request in practice. */
        const val PAGE_SIZE = 100

        /** A backstop, not a limit: it exists so a misbehaving `next` cannot loop forever. */
        const val MAX_PAGES = 20
    }
}
