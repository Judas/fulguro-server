package com.fulgurogo.api.db.model

import com.fulgurogo.house.HousePeriod

/**
 * One session's page: its pairings, and the players it could not pair.
 *
 * The envelope repeats [season], [period] and [sessionCount] from [ApiLeague] on purpose — the site can land on a session
 * directly, and a page that needs a second request to know what season it is showing is a page that renders in two steps.
 *
 * [matches] is empty for a session not yet drawn, which is not the same thing as a session drawn with nobody to pair: that
 * one has [exemptions] instead. `session.drawn` is what tells the two apart.
 */
data class ApiLeagueSessionDetails(
    val season: String,
    val period: HousePeriod,
    val sessionCount: Int,
    val session: ApiLeagueSession,
    val matches: List<ApiLeagueMatch> = listOf(),
    val exemptions: List<ApiLeagueExemption> = listOf()
)
