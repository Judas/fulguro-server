package com.fulgurogo.house.db.model

/**
 * The seven scoring columns and the total they were worth, wherever they come from: one game ([HousePoints]), or a
 * player's line in a house ranking ([HouseRankedMember]), which is also how a player's own season total is read.
 *
 * [total] is **not** the sum of the seven columns and must never be recomputed as one. The scale is divided by the
 * board — halved on 13×13, quartered on 9×9, both rounded up — and rounding up is exactly what a per-column split
 * cannot reproduce: ceil(11/2) is 6, while halving and rounding up each of the seven columns gives 7. So the awarded
 * value is carried rather than derived: `HousePointsCalculator` computes it once per game, the register stores it, and
 * SQL sums that column. Everything that ranks, prints or totals reads this field.
 *
 * The seven columns stay raw, at full value, and are the detail of how the game was judged: what it was worth is
 * [total], why it was worth it is the breakdown.
 */
interface HousePointsBreakdown {
    val played: Int
    val goldOpponent: Int
    val rivalHouse: Int
    val longGame: Int
    val victory: Int
    val evenGame: Int
    val ranked: Int

    /** What the register credited, board coefficient included. Equal to the sum of the seven columns on 19×19 only. */
    val total: Int
}
