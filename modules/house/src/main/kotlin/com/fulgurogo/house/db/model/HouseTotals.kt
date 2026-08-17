package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * The raw aggregate row behind a house's figures, straight out of SQL and not meant to leave the accessor —
 * [HouseStanding] is what callers get.
 *
 * Both fields are filled by the one query that builds it, `houseTotals`, and both are read by `standings` and
 * `details`. Nothing else maps this class — a partial select would leave the other field at 0 rather than say so, which
 * is why it is not a general-purpose model.
 */
@GenerateNoArgConstructor
data class HouseTotals(
    val houseId: Int,
    val memberCount: Int,
    val totalPoints: Int
)
