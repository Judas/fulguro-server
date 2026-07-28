package com.fulgurogo.house.db.model

/**
 * The seven scoring columns, wherever they come from: one game ([HousePoints]), a player's season ([HousePointsTotal]),
 * or a player's line in a house ranking ([HouseRankedMember]).
 *
 * [total] lives here so the scale is summed in exactly one place. There is one other copy of it, unavoidably: the SQL
 * expression `HouseDatabaseAccessor` uses to total a house includes the points of players who have since left, which
 * no list of current members can produce. Adding a column to the scale means changing both.
 */
interface HousePointsBreakdown {
    val played: Int
    val goldOpponent: Int
    val rivalHouse: Int
    val longGame: Int
    val victory: Int
    val evenGame: Int
    val ranked: Int

    fun total(): Int = played + goldOpponent + rivalHouse + longGame + victory + evenGame + ranked
}
