package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.HousePointsBreakdown

/**
 * The seven scoring columns as the website reads them, plus the total.
 *
 * ⚠ [total] is **not** the sum of the seven columns and the site must print it rather than add them up. The scale is
 * divided by the board — halved on 13×13, quartered on 9×9, both rounded up — so below 19×19 a game credits less than
 * its breakdown says. Summing the columns client-side gives a bigger figure than the one the server ranks on, and it
 * would look right, which is the whole reason [total] is in the response at all: it comes from
 * [HousePointsBreakdown.total], the one value the server itself ranks and totals with.
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
            total = points.total
        )
    }
}
