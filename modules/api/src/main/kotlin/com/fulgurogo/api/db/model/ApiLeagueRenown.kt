package com.fulgurogo.api.db.model

import com.fulgurogo.league.db.model.LeagueRenown

/**
 * A player's renown broken down, plus the total.
 *
 * [total] is in the response on purpose, and this is the one place the API produces it: it comes from
 * [LeagueRenown.total], so the figure the site prints is the one the server ranked on. Left out, every consumer would
 * re-add the scale itself, and a scale that changed would then be wrong on the site while staying right on the server —
 * the quietest possible way for the two to drift.
 */
data class ApiLeagueRenown(
    val playedPoints: Int,
    val victoryPoints: Int,
    val perfectBonus: Int,
    val total: Int
) {
    companion object {
        fun from(renown: LeagueRenown): ApiLeagueRenown = ApiLeagueRenown(
            playedPoints = renown.playedPoints,
            victoryPoints = renown.victoryPoints,
            perfectBonus = renown.perfectBonus,
            total = renown.total()
        )
    }
}
