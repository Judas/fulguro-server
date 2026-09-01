package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * A current member of a house with what they have scored for it this season, and their place in it.
 *
 * Only points earned *for this house* are counted: a player who changed house starts again at zero, their old points
 * staying with the old house. Within one season that case cannot normally arise — a change is only ever applied when a
 * season opens — but the accessor matches on house anyway rather than relying on that.
 *
 * The seven columns are raw sums and [total] is the sum of what each game actually credited, so on a player who
 * has been playing small boards the two do not match — see [HousePointsBreakdown].
 *
 * [rank] is filled in Kotlin after sorting, not by SQL, so that the order shown, [total] and the rank can never
 * disagree. It is a competition rank: equal totals share a rank and the next one skips (1, 2, 2, 4).
 */
@GenerateNoArgConstructor
data class HouseRankedMember(
    val discordId: String,
    val houseId: Int,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    override val played: Int,
    override val goldOpponent: Int,
    override val rivalHouse: Int,
    override val longGame: Int,
    override val victory: Int,
    override val evenGame: Int,
    override val ranked: Int,
    /** The sum of the per-game totals, coefficients already applied — not the sum of the seven columns above. */
    override val total: Int,
    val rank: Int = 0
) : HousePointsBreakdown
