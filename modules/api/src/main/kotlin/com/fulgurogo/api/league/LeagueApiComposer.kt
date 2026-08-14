package com.fulgurogo.api.league

import com.fulgurogo.api.db.model.*
import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.league.LeagueSession
import com.fulgurogo.league.db.LeagueDatabaseAccessor
import com.fulgurogo.league.Session
import com.fulgurogo.league.db.model.LeagueMatch
import com.fulgurogo.league.db.model.LeagueSessionState
import com.fulgurogo.league.db.model.LeagueSide
import com.fulgurogo.league.db.model.LeagueStanding

/**
 * Builds the league's API responses out of the accessor's rows.
 *
 * It exists because the three routes — the page, one session, and the block on a profile — need the same three things:
 * the season's standings, a crest per house, and the ability to turn a Discord id into a displayable player. Composing
 * that in each handler would be three copies of the same joins, free to drift; here the shapes agree by construction.
 *
 * The standings are the source of every identity, and that is deliberate rather than incidental. Everyone a session can
 * name — the two sides of a match, an exempted player, an opponent on a profile — is a member of the season, so
 * `standings(season)` already holds their name, avatar and house. It costs no extra query, and it means a player is
 * described the same way wherever they appear.
 */
class LeagueApiComposer(private val season: String) {
    private val standings: List<LeagueStanding> = LeagueDatabaseAccessor.standings(season)
    private val byDiscordId: Map<String, LeagueStanding> = standings.associateBy { it.discordId }

    /**
     * One crest per house id, read once.
     *
     * Driven from `houses` rather than from the members, so a house nobody is in still resolves — and a player whose
     * `house_id` somehow names no house comes back with a null crest instead of failing the whole response.
     */
    private val crests: Map<Int, ApiLeagueCrest> = HouseDatabaseAccessor.houses()
        .associate { it.id to ApiLeagueCrest.from(it) }

    val sessionCount: Int = LeagueSession.count(season)

    /**
     * Every session's state, read once and indexed by number.
     *
     * A session with no row is absent, which reads as "not drawn yet" — the honest answer, since the row is created by the
     * draw. Doing this per session would be sixteen round trips for one page.
     *
     * `by lazy` and not eager: the profile block never looks at a session's state, and that is the route this composer is
     * built on most often. Eager, it cost every profile view a query nothing read.
     */
    private val states: Map<Int, LeagueSessionState> by lazy {
        LeagueDatabaseAccessor.sessionStates(season).associateBy { it.session }
    }

    /** The whole calendar: the sixteen sessions with their bounds and their state. */
    fun calendar(): List<ApiLeagueSession> =
        LeagueSession.sessions(season).map { ApiLeagueSession.from(it, states[it.number]) }

    /** One session, for the routes that already hold it. */
    fun session(session: Session): ApiLeagueSession = ApiLeagueSession.from(session, states[session.number])

    /** The whole standings, already ranked by the accessor. */
    fun standings(): List<ApiLeagueStanding> = standings.map { ApiLeagueStanding.from(it, crestOf(it)) }

    /**
     * A player as somebody to name rather than to rank.
     *
     * Falls back to the bare id for somebody the standings do not hold, which is reachable: `CleanService` purges the
     * academy row of a player who left the Discord server but never their matches, so a past pairing can name a player
     * the season no longer knows. Dropping the match would hide a game that was really played.
     */
    fun member(discordId: String): ApiLeagueMember = byDiscordId[discordId]
        ?.let { ApiLeagueMember.from(it, crestOf(it)) }
        ?: ApiLeagueMember.unknown(discordId)

    fun match(match: LeagueMatch): ApiLeagueMatch =
        ApiLeagueMatch.from(match, member(match.blackDiscordId), member(match.whiteDiscordId))

    /** The session's pairings and the players it could not pair. */
    fun sessionMatches(session: Int): List<ApiLeagueMatch> =
        LeagueDatabaseAccessor.matches(season, session).map { match(it) }

    fun sessionExemptions(session: Int): List<ApiLeagueExemption> =
        LeagueDatabaseAccessor.exemptionsOf(season, session)
            .map { ApiLeagueExemption.from(it, member(it.discordId)) }

    /**
     * The league block of a profile, or null when the player was never a member of this season.
     *
     * Their line comes from the same standings every other route reads, so the rank a profile prints is the rank the page
     * shows — two separate computations could straddle a tick and disagree.
     */
    fun playerBlock(discordId: String, period: HousePeriod): ApiPlayerLeague? {
        val standing = byDiscordId[discordId] ?: return null

        return ApiPlayerLeague(
            season = season,
            period = period,
            sessionCount = sessionCount,
            active = standing.active,
            rank = standing.rank,
            played = standing.played,
            won = standing.won,
            lost = standing.lost,
            exempted = standing.exempted,
            renown = ApiLeagueRenown.from(standing.renown),
            matches = LeagueDatabaseAccessor.matchesOf(season, discordId).map { playerMatch(discordId, it) }
        )
    }

    /**
     * One match from one player's side.
     *
     * [ApiPlayerLeagueMatch.won] is left **null** when the match names no winner, and that is not the same as false: a
     * match still to play, one the settlement voided and an annulled game all have no winner, and showing them as defeats
     * would invent losses.
     */
    private fun playerMatch(discordId: String, match: LeagueMatch): ApiPlayerLeagueMatch {
        val isBlack = match.blackDiscordId == discordId
        val winner = match.winner()

        return ApiPlayerLeagueMatch(
            session = match.session,
            // The player's own side. Lowercased from the enum rather than a literal, so it cannot drift from
            // the `black` / `white` that `result` uses.
            color = (if (isBlack) LeagueSide.BLACK else LeagueSide.WHITE).name.lowercase(),
            opponent = member(if (isBlack) match.whiteDiscordId else match.blackDiscordId),
            spectatorLink = match.spectatorLink,
            result = match.result,
            won = winner?.let { it == discordId }
        )
    }

    private fun crestOf(standing: LeagueStanding): ApiLeagueCrest? = standing.houseId?.let { crests[it] }
}
