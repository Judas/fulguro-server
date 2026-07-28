package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * The raw aggregate row behind a house's figures, straight out of SQL and not meant to leave the accessor —
 * [HouseStanding] is what callers get.
 *
 * Both fields are filled by `standings`. `memberCounts` only selects [memberCount] and leaves [totalPoints] at 0,
 * which is safe to ignore there but is why this class is not a general-purpose model.
 */
@GenerateNoArgConstructor
data class HouseTotals(
    val houseId: Int,
    val memberCount: Int,
    val totalPoints: Int
)
