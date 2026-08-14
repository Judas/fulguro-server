package com.fulgurogo.api.db.model

import com.fulgurogo.house.HousePeriod

/**
 * The "League" page: where the calendar stands, and the full standings best first.
 *
 * [period] and [season] travel with the standings rather than living in a route of their own, exactly as on the house
 * routes, so the site never recomputes the calendar nor pays for a second round trip. The server stays the only thing
 * that knows when a season runs.
 *
 * [sessionCount] is in the response because the perfect-attendance bonus is defined against it — a site that hardcoded 16
 * would be wrong the day the split changed, while the server would still be right.
 *
 * [currentSession] is null out of season and inside the two holes of the calendar, the first half of September and the
 * second half of December. Null there is the answer, not a gap: no session is running.
 *
 * [sessions] is the **whole** calendar, all sixteen with their bounds and their state, so a page can draw the season's
 * table from one request. It is there rather than left to sixteen calls on the session route, and it is also what makes
 * the two holes in the calendar visible: the fortnights that are not sessions simply have no entry, and the numbering runs
 * 1 to 16 without them. [currentSession] is one of these entries repeated, kept so a caller that only wants "what is
 * running" does not have to search the list.
 */
data class ApiLeague(
    val season: String,
    val period: HousePeriod,
    val sessionCount: Int,
    val currentSession: ApiLeagueSession? = null,
    val sessions: List<ApiLeagueSession> = listOf(),
    val standings: List<ApiLeagueStanding> = listOf()
)
