package com.fulgurogo.api.db.model

import com.fulgurogo.league.db.model.LeagueStanding

/**
 * One line of the league standings.
 *
 * [rank] is a competition rank — equal totals share it and the next one skips (1, 2, 2, 4) — so the site prints it and
 * never counts rows.
 *
 * [exempted] is here rather than being an internal detail, and it earns its place: the perfect-attendance bonus is
 * `played + exempted == sessionCount`, so a page showing only [played] would make a bonus look wrongly awarded.
 *
 * [played], [won] and [lost] do not reconcile with the number of sessions: a match still open or voided by a settlement
 * counts in none of them.
 *
 * Inactive players are in the standings, with `active: false`, and ranked like everyone else — their renown stays theirs,
 * they are simply no longer drawn.
 */
data class ApiLeagueStanding(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val house: ApiHouseCrest? = null,
    val active: Boolean,
    val rank: Int,
    val played: Int,
    val won: Int,
    val lost: Int,
    val exempted: Int,
    val renown: ApiLeagueRenown
) {
    companion object {
        fun from(standing: LeagueStanding, crest: ApiHouseCrest?): ApiLeagueStanding = ApiLeagueStanding(
            discordId = standing.discordId,
            discordName = standing.discordName,
            discordAvatar = standing.discordAvatar,
            house = crest,
            active = standing.active,
            rank = standing.rank,
            played = standing.played,
            won = standing.won,
            lost = standing.lost,
            exempted = standing.exempted,
            renown = ApiLeagueRenown.from(standing.renown)
        )
    }
}
