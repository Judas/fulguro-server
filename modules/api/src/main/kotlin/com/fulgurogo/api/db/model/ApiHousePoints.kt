package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.HousePointsBreakdown

/**
 * The seven scoring columns as the website reads them, plus the total.
 *
 * [total] is in the response on purpose, and this is the one place the API produces it: it comes from
 * [HousePointsBreakdown.total], so the figure the site prints is the one the server ranks on. Left out, every consumer
 * would sum the seven columns itself, and a scale that gains a column would then be wrong on the site while staying
 * right on the server — the quietest possible way for the two to drift apart.
 */
data class ApiHousePoints(
    val played: Int,
    val goldOpponent: Int,
    val rivalHouse: Int,
    val longGame: Int,
    val victory: Int,
    val evenGame: Int,
    val ranked: Int,
    val total: Int
) {
    companion object {
        fun from(points: HousePointsBreakdown): ApiHousePoints = ApiHousePoints(
            played = points.played,
            goldOpponent = points.goldOpponent,
            rivalHouse = points.rivalHouse,
            longGame = points.longGame,
            victory = points.victory,
            evenGame = points.evenGame,
            ranked = points.ranked,
            total = points.total()
        )
    }
}
