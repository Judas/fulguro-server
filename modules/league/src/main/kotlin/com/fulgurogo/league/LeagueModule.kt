package com.fulgurogo.league

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.house.HouseSeason
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * This module is in charge of the league: academy membership, session pairings, OGS matches and renown.
 */
object LeagueModule {
    const val TAG = "LGE"

    private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

    private val sessionService = LeagueSessionService()

    fun init() {
        logCalendar()
        LeagueTestPlayers.logState()
        LeagueSession.logState()
        sessionService.start()
    }

    /**
     * Two lines at startup with the calendar the module is going to pair on.
     *
     * The season and the sessions are read for the same instant rather than each calling [ZonedDateTime.now] on its own,
     * so the line cannot show a season and a current session read on either side of a session boundary. Printing the
     * whole split, and not just the current session, is what makes the two holes in the calendar — the first half of
     * September and the second half of December — visible without running the app on those dates.
     */
    private fun logCalendar() {
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)
        val sessions = LeagueSession.sessions(season)
        val current = LeagueSession.current(season, now)

        log(TAG, "Season $season has ${sessions.size} sessions, current is ${current?.number ?: "none"}")
        log(TAG, "Sessions (end excluded): " + sessions.joinToString(" ") {
            "${it.number}:${DAY_MONTH.format(it.start)}-${DAY_MONTH.format(it.end)}"
        })
    }
}
