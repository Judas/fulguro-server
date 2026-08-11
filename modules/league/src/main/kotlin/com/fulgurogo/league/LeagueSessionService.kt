package com.fulgurogo.league

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.common.utilities.toStartOfDay
import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.HouseSeason
import com.fulgurogo.league.LeagueModule.TAG
import com.fulgurogo.league.db.LeagueDatabaseAccessor
import com.fulgurogo.league.db.model.LeagueExemption
import com.fulgurogo.league.db.model.LeagueMatch
import com.fulgurogo.league.db.model.LeagueSide
import com.fulgurogo.league.ogs.OgsLeagueClient
import com.fulgurogo.league.ogs.model.LeagueLoser
import com.fulgurogo.league.ogs.model.OgsLeagueMatch
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * The whole life of the league, on one tick: opening a season, reconciling memberships, drawing a session, creating the
 * OGS challenges, sending the links, collecting the results, settling what never got played, and closing the year.
 *
 * Ten minutes is plenty, as for `HouseSeasonService`: every event here is dated to the day. It also means the start of a
 * session is seen about 1400 times, so **no branch may rely on the calendar alone** — the calendar says "session 8 has
 * started", not "session 8 has been dealt with". `league_sessions` says the second, and every branch claims its column
 * before doing anything a second run would repeat.
 *
 * The order of the blocks is not cosmetic, and each dependency is one-directional:
 *
 * - Reconciliation runs **before** the draw so a player who left yesterday is not paired, and a player who joined
 *   yesterday has their `member_id` at OGS in time to receive a challenge.
 * - The previous session's settlement also runs **before** the draw, so a tick at 15 September 07:00 closes the session
 *   that ended before opening the one that starts. Both work either way, but the logs of a single tick that drew first
 *   and closed afterwards are unreadable.
 * - The DMs run **after** the challenge creation, since there is no link to send before it, and the channel announcement
 *   after the draw so it can never describe a half-written one.
 *
 * ⚠ In dev this service is what makes the league reach production, because there is one OGS league and dev shares it.
 * `league.test.players` is the only guard, and it is applied here too — on the registration queue — rather than trusted
 * from the join route alone.
 */
class LeagueSessionService : PeriodicFlowService(INITIAL_DELAY_IN_SECONDS, INTERVAL_IN_SECONDS) {
    private val ogs = OgsLeagueClient()

    /**
     * One clock read for the whole tick.
     *
     * Asking again mid-tick could straddle a session boundary or midnight on 1 June, and then a single tick would draw
     * for one session while settling against another.
     */
    override suspend fun onTick() {
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)
        val period = HouseSeason.period(now)

        if (period == HousePeriod.SEASON) openSeason(season)

        reconcileMembers(season)
        registerWithOgs()

        // No period check on purpose: session 16 ends on 1 June and is settled in VACATION. The window belongs to the
        // session calendar, not to the houses' one -- the same reason HousePointsService ignores the period.
        settleEndedSessions(season, now)

        LeagueSession.current(season, now)?.let { session ->
            drawSession(season, session, now)
            createMissingChallenges(season, session.number)
            sendMissingLinks(season, session.number)
            announceDraw(season, session.number)
            collectResults(season, session.number)
        }

        if (period == HousePeriod.VACATION) closeSeason(season)
    }

    /**
     * Records that a season has begun. Nothing else: there is nothing to create at OGS, and the academies are empty by
     * construction since the season is part of their key.
     *
     * It exists only to give the closing recap something to anchor on — `opened IS NULL` is what tells a season that
     * really ran from one that only ever existed as a name.
     */
    private fun openSeason(season: String) {
        if (LeagueDatabaseAccessor.seasonState(season)?.opened != null) return
        if (LeagueDatabaseAccessor.openSeason(season)) log(TAG, "League season $season opened")
    }

    /** Drops the members who lost their house or their OGS account, however that happened. See step 4. */
    private fun reconcileMembers(season: String) {
        val dropped = LeagueDatabaseAccessor.deactivateIneligible(season)
        if (dropped > 0) log(TAG, "Deactivated $dropped member(s) with no house or no OGS account")
    }

    /**
     * Registers with OGS the players who joined but are not known there yet.
     *
     * ⚠ The sandbox is re-applied here, and this is the one place it protects **production from dev** rather than the
     * other way round: registering a member is the act of entering the shared OGS league. The join route already refuses
     * outsiders, so a skip should never fire — which is exactly why it is logged if it does.
     *
     * `ogs_registered` is stamped only on success, so a failure is retried next tick. Replaying the call is harmless —
     * OGS answers 200 instead of 201 — but it must not be replayed for everyone every tick, which is what this queue is.
     */
    private fun registerWithOgs() {
        LeagueDatabaseAccessor.playersToRegister().forEach { player ->
            if (!LeagueTestPlayers.isAllowed(player.discordId)) {
                log(TAG, "Not registering ${player.discordId} with OGS: outside the dev sandbox")
                return@forEach
            }

            if (ogs.registerMember(LeagueMemberId.of(player.discordId))) {
                LeagueDatabaseAccessor.markRegistered(player.discordId)
                log(TAG, "Registered ${player.discordId} with the OGS league")
            }
        }
    }

    /**
     * Settles every session whose end has passed and that is not settled yet: one last sweep, then void what has no
     * result, then claim the column.
     *
     * That order is the deadline itself. A game has to be *started* before midnight on the last day and *finished* by the
     * settlement, so the seven hours between the two are the ingestion margin — and one last sweep is what stops a match
     * being condemned when its result was one call away.
     *
     * The claim comes last so an interrupted settlement resumes where it stopped instead of burning its only chance.
     *
     * A session that was never drawn has no row, so [LeagueDatabaseAccessor.claimSettlement] matches nothing and answers
     * false. Nothing to settle, nothing logged.
     */
    private fun settleEndedSessions(season: String, now: ZonedDateTime) {
        if (!isMorningWindow(now)) return

        LeagueSession.ended(season, now).forEach { session ->
            val state = LeagueDatabaseAccessor.sessionState(season, session.number) ?: return@forEach
            if (state.settled != null) return@forEach

            collectResults(season, session.number)

            val voided = LeagueDatabaseAccessor.markUnplayed(season, session.number)
            if (LeagueDatabaseAccessor.claimSettlement(season, session.number))
                log(TAG, "Session ${session.number} of $season settled, $voided match(es) left unplayed")
        }
    }

    /**
     * Draws the session in progress, or repairs a draw that came out empty.
     *
     * The guard is claimed **before** drawing, the opposite trade from the closing recap, and for the reason the houses'
     * daily ranking makes: over ~1400 ticks a session, a double draw is the failure to avoid — two challenges per player
     * — while a missed draw is caught by the next tick ten minutes later.
     *
     * The condition is "a session is running, it has not been drawn, and it is the morning window" and **not** "it is the
     * first day of the session". That is what makes the draw self-repairing: a server down on the 15th draws on the 16th
     * with fourteen days left instead of fifteen. Tying it to the first day would lose a whole session to a two-hour
     * outage — and the log says how late it is, because nothing else would.
     */
    private fun drawSession(season: String, session: Session, now: ZonedDateTime) {
        if (!isMorningWindow(now)) return

        if (LeagueDatabaseAccessor.claimDraw(season, session.number)) {
            val late = ChronoUnit.DAYS.between(session.start, now)
            val lateness = if (late > 0) ", $late day(s) late" else ""
            log(TAG, "Drawing session ${session.number} of $season$lateness")
            performDraw(season, session.number)
        } else {
            redrawIfEmpty(season, session.number, now)
        }
    }

    /**
     * Redraws a session marked as drawn that holds neither a match nor an exemption.
     *
     * That state has **two indistinguishable causes**: a crash between `claimDraw` and the writes, and a draw that was
     * legitimately empty — nobody registered on 15 September, or every active player in one house. The condition does not
     * try to tell them apart, it makes the distinction unnecessary: **redraw an empty session when there is something to
     * fill it with now.** The crash case has candidates, so it is redrawn the next morning. The normal case has nothing to
     * pair, so it writes nothing and logs nothing — which is the point, and what makes this preferable to a `candidates`
     * column that would have reported a non-existent outage for a fortnight.
     *
     * The daily guard is claimed **after** finding there are pairs to write, not before, or the problem would just have
     * moved one step along.
     *
     * ⚠ Accepted side effect: the draw becomes repairable mid-session. Three players registering on 16 September, on a
     * session 1 drawn empty on the 15th, are paired on the 17th rather than waiting for 1 October.
     */
    private fun redrawIfEmpty(season: String, session: Int, now: ZonedDateTime) {
        // Drawn and empty means: no match AND no exemption. A session with exemptions and no match *was* drawn -- it
        // simply had nobody to pair -- and redrawing it would be the non-existent outage this condition exists to avoid.
        if (LeagueDatabaseAccessor.matches(season, session).isNotEmpty()) return
        if (LeagueDatabaseAccessor.exemptionsOf(season, session).isNotEmpty()) return

        val draw = drawFor(season)
        if (draw.pairings.isEmpty()) return

        if (!LeagueDatabaseAccessor.claimRedraw(season, session, now.toStartOfDay().toDate())) return

        log(TAG, "Redrawing session $session of $season, which was drawn empty")
        writeDraw(season, session, draw)
        createMissingChallenges(season, session)
    }

    /** Draws, writes, then creates the OGS challenges. */
    private fun performDraw(season: String, session: Int) {
        val draw = drawFor(season)
        writeDraw(season, session, draw)
        createMissingChallenges(season, session)
    }

    private fun drawFor(season: String): Draw = LeaguePairing.draw(
        candidates = LeagueDatabaseAccessor.candidates(season),
        history = LeagueDatabaseAccessor.pastOpponents(season),
        exemptions = LeagueDatabaseAccessor.exemptions(season)
    )

    /**
     * Writes a draw: the exemptions first, then the matches.
     *
     * That order is deliberate. Exemptions depend on nobody and cost nothing, while the matches are followed by network
     * calls — so a failure on the OGS side must not be able to cost a player the end-of-season bonus that an exemption
     * protects.
     *
     * Both writes are `INSERT IGNORE`, so a redraw landing on rows that already exist adds nothing.
     */
    private fun writeDraw(season: String, session: Int, draw: Draw) {
        val exemptions = draw.exemptions.map {
            LeagueExemption(season = season, session = session, discordId = it.discordId, reason = it.reason.name)
        }
        val written = LeagueDatabaseAccessor.addExemptions(exemptions)

        val matches = draw.pairings.map { pairing ->
            LeagueMatch(
                season = season,
                session = session,
                blackDiscordId = pairing.black.discordId,
                whiteDiscordId = pairing.white.discordId,
                // Frozen here, so an academy's total never moves when a player changes house or leaves it.
                blackHouseId = pairing.black.houseId,
                whiteHouseId = pairing.white.houseId,
                pairingScore = pairing.score,
                leagueMatchId = leagueMatchId(season, session, pairing.black.discordId),
                created = Date()
            )
        }
        val inserted = LeagueDatabaseAccessor.addMatches(matches)

        log(TAG, "Session $session of $season: $inserted match(es) and $written exemption(s) written")
    }

    /**
     * Creates the OGS challenge of every match of the session that has none, and records the three links.
     *
     * The same function serves the draw and the retry, which is what makes a partly failed draw a non-event: a match
     * whose creation failed stays in the database without links, and the next tick replays the call. OGS is idempotent on
     * `league_match_id`, so the replay returns the existing challenge rather than creating a second one — **provided the
     * payload is identical**, which is why nothing in it may become time-dependent.
     */
    private fun createMissingChallenges(season: String, session: Int) {
        LeagueDatabaseAccessor.matches(season, session)
            .filter { it.ogsMatchId == null }
            .forEach { match ->
                val created = ogs.createMatch(
                    blackMemberId = LeagueMemberId.of(match.blackDiscordId),
                    whiteMemberId = LeagueMemberId.of(match.whiteDiscordId),
                    leagueMatchId = match.leagueMatchId,
                    season = season,
                    sessionNumber = session
                ) ?: return@forEach

                LeagueDatabaseAccessor.setMatchChallenge(
                    season = season,
                    session = session,
                    blackDiscordId = match.blackDiscordId,
                    ogsMatchId = created.id,
                    blackInvite = created.blackInvite,
                    whiteInvite = created.whiteInvite,
                    spectatorLink = created.spectatorLink
                )
                log(TAG, "Challenge ${created.id} created for ${match.leagueMatchId}")
            }
    }

    /**
     * Sends the invitation links that are still owed, one DM per side.
     *
     * Each side is stamped only when its own DM succeeded, so a player whose DMs are closed does not stop their opponent
     * from being told, and neither is recorded as notified when they were not. The links stay in the database either way,
     * because resending one by hand is the fallback and it happens days later.
     */
    private fun sendMissingLinks(season: String, session: Int) {
        LeagueDatabaseAccessor.unnotifiedMatches(season, session).forEach { match ->
            if (match.blackNotified == null && LeagueNotifier.notifyChallenge(match, LeagueSide.BLACK))
                LeagueDatabaseAccessor.markNotified(season, session, match.blackDiscordId, LeagueSide.BLACK)

            if (match.whiteNotified == null && LeagueNotifier.notifyChallenge(match, LeagueSide.WHITE))
                LeagueDatabaseAccessor.markNotified(season, session, match.blackDiscordId, LeagueSide.WHITE)
        }
    }

    /**
     * Announces the draw on the channel, once.
     *
     * Claims first and announces after, unlike the closing recap: this fires sixteen times a season and a duplicate is
     * spam, while a miss is invisible. The recap fires once a year and makes the opposite trade.
     *
     * A session nobody has drawn claims nothing, so there is no announcement of an empty draw.
     */
    private fun announceDraw(season: String, session: Int) {
        val matches = LeagueDatabaseAccessor.matches(season, session)
        if (matches.isEmpty()) return
        if (!LeagueDatabaseAccessor.claimNotification(season, session)) return

        log(TAG, "Announcing the draw of session $session of $season")
        LeagueNotifier.notifyDraw(
            season = season,
            session = session,
            matches = matches,
            exempted = LeagueDatabaseAccessor.exemptionsOf(season, session).map { it.discordId }
        )
    }

    /**
     * Reads the session's matches at OGS in **one** request and writes back what has moved. This is step 8's sweep, and
     * the only path by which a result ever arrives.
     *
     * One call rather than one per match, filtered on the `league_match_id` prefix, which also means a dev run only ever
     * sees its own matches. Nothing is written for a match whose row we do not hold — a prefix collision would be a bug,
     * not something to act on.
     *
     * Two writes, both guarded so they are safe to repeat every ten minutes for a fortnight: the game id once it exists,
     * and the result once OGS says the match is finished. `finishMatch` carries `WHERE result IS NULL`, so a match the
     * settlement already voided is never resurrected, and a result already recorded is never rewritten.
     */
    private fun collectResults(season: String, session: Int) {
        val rows = LeagueDatabaseAccessor.matches(season, session)
        if (rows.isEmpty()) return

        val byLeagueId = rows.associateBy { it.leagueMatchId }
        ogs.sessionMatches(sessionPrefix(season, session)).forEach { remote ->
            val row = byLeagueId[remote.leagueMatchId] ?: return@forEach

            if (row.ogsGameId == null && remote.game != null) {
                LeagueDatabaseAccessor.setMatchGame(
                    season = season,
                    session = session,
                    blackDiscordId = row.blackDiscordId,
                    ogsGameId = remote.game,
                    goldId = "OGS_${remote.game}"
                )
                log(TAG, "Match ${row.leagueMatchId} is game OGS_${remote.game}")
            }

            if (row.result == null && remote.finished == true) {
                val result = resultOf(remote)
                if (LeagueDatabaseAccessor.finishMatch(season, session, row.blackDiscordId, result))
                    log(TAG, "Match ${row.leagueMatchId} finished: $result")
            }
        }
    }

    /**
     * What OGS's answer means for the league.
     *
     * Annulment is tested **first**, and that is load-bearing: an annulled match still names a loser. Measured on a real
     * one — `annulled: true` together with `black_lost: true, white_lost: false` — so reading the flags first would turn a
     * game that does not stand into a win for white. [OgsLeagueMatch.loser] already refuses to name a side on an annulled
     * match; this branch exists so the stored value says *annulled* rather than merely *no winner*, which is what a player
     * asking about it wants to hear.
     *
     * The last branch is a finished match naming neither side and not annulled. Unreachable as things stand — japanese
     * rules put komi at a half point — and recorded as [LeagueMatch.JIGO] rather than as an annulment, because calling it
     * one would put a claim in the data that is not true. Either way it is played with no winner: 2 points each.
     */
    private fun resultOf(match: OgsLeagueMatch): String = when {
        match.isAnnulled() -> LeagueMatch.ANNULLED
        match.loser() == LeagueLoser.BLACK -> LeagueMatch.WHITE_WINS
        match.loser() == LeagueLoser.WHITE -> LeagueMatch.BLACK_WINS
        else -> LeagueMatch.JIGO
    }

    /**
     * Closes a season: announces the recap, then records the closure.
     *
     * Announce then record, the opposite of the draw: a crash between the two re-announces the recap next tick, whereas
     * recording first would burn the guard and lose it for good. A duplicate is visible and deletable; a missing
     * end-of-season message is noticed a year later.
     *
     * ⚠ The condition on the **last session being settled** is what stops the recap describing a false ranking. Both
     * events fall on 1 June, in the same window and therefore in the same tick: without it the block order would be
     * enough, but enough *by accident*.
     *
     * Its consequence, and it is accepted: a season whose last session was never drawn has no row to settle, so it never
     * closes and never announces. That is the right silence — there is nothing to announce — but it does mean a season
     * interrupted for the whole of late May stays open.
     */
    private fun closeSeason(season: String) {
        val state = LeagueDatabaseAccessor.seasonState(season)
        if (state?.opened == null || state.closed != null) return

        val last = LeagueSession.sessions(season).lastOrNull() ?: return
        if (LeagueDatabaseAccessor.sessionState(season, last.number)?.settled == null) return

        val standings = LeagueDatabaseAccessor.standings(season)
        log(TAG, "Closing league season $season, ${standings.size} player(s) ranked")
        LeagueNotifier.notifySeasonRecap(season, standings)

        if (LeagueDatabaseAccessor.closeSeason(season)) log(TAG, "League season $season closed")
    }

    /** `<db.name>_<season>_<session>_`, the prefix that identifies one session of one environment at OGS. */
    private fun sessionPrefix(season: String, session: Int): String =
        "${Config.get("db.name")}_${season}_${session}_"

    /** The id pushed to OGS. Derived from the primary key, so a replay carries the same one. */
    private fun leagueMatchId(season: String, session: Int, blackDiscordId: String): String =
        "${sessionPrefix(season, session)}$blackDiscordId"

    /**
     * 7:00 to 9:59, the houses' daily-ranking window, for the same reason: the only moment a notification stands a chance
     * of being read the same day. A draw at midnight would DM everyone in the middle of the night.
     */
    private fun isMorningWindow(now: ZonedDateTime): Boolean = now.hour in FIRST_HOUR..LAST_HOUR

    companion object {
        /** Behind the houses' 90s and 120s, so a cold start does not open every outbound connection at once. */
        private const val INITIAL_DELAY_IN_SECONDS = 150L

        /** Ten minutes, as `house season`, `ping` and `clean` use. */
        private const val INTERVAL_IN_SECONDS = 600L

        private const val FIRST_HOUR = 7
        private const val LAST_HOUR = 9
    }
}
