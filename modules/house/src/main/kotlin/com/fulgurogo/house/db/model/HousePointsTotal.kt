package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/** A player's points for one season, summed per type. All zeroes when they have scored nothing yet. */
@GenerateNoArgConstructor
data class HousePointsTotal(
    override val played: Int,
    override val goldOpponent: Int,
    override val rivalHouse: Int,
    override val longGame: Int,
    override val victory: Int,
    override val evenGame: Int,
    override val ranked: Int
) : HousePointsBreakdown
