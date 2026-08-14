package com.fulgurogo.api.db.model

import com.fulgurogo.house.HousePeriod

/**
 * The league block of a player's profile: where they stand, and every match of their season.
 *
 * Null on the profile of somebody who was never a member of the current season — the key is present with a null value,
 * like `house`, rather than absent.
 *
 * Composed by the handler from the league accessor, **not** added to the `api_players` view: it is counted over the
 * current season, which only Kotlin knows, and this way there is no view to alter on the production server.
 *
 * [rank] is a competition rank over the whole league, so it is a figure to print rather than a position to count. And
 * [exempted] is here for the same reason it is in the standings: `played + exempted == sessionCount` is the bonus, so a
 * profile showing only [played] would make the bonus look wrongly awarded.
 */
data class ApiPlayerLeague(
    val season: String,
    val period: HousePeriod,
    val sessionCount: Int,
    val active: Boolean,
    val rank: Int,
    val played: Int,
    val won: Int,
    val lost: Int,
    val exempted: Int,
    val renown: ApiLeagueRenown,
    val matches: List<ApiPlayerLeagueMatch> = listOf()
)

/**
 * One of the player's matches, from their own side.
 *
 * [color] is `black` or `white` — theirs, not the winner's — and [opponent] the other player, so a profile needs no
 * arithmetic to work out which side of a pairing it is looking at.
 *
 * [won] is a three-state answer and null is meaningful: true when this player won, false when they lost, **null when
 * there is no winner** — a match not yet played, one the settlement voided, or an annulled game. Reading a null as false
 * would show a defeat that never happened.
 */
data class ApiPlayerLeagueMatch(
    val session: Int,
    val color: String,
    val opponent: ApiLeagueMember,
    val spectatorLink: String? = null,
    val result: String? = null,
    val won: Boolean? = null
)
