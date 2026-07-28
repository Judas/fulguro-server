package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * One row of the points register: what a single game earned a single player.
 *
 * [houseId] and [season] are frozen when the row is written, on purpose — a player changing house never moves the
 * points they already earned, so a house total only ever grows, and the register is its own history.
 *
 * The primary key `(goldId, discordId)` is the whole of the scanner's idempotence: a game cannot be counted twice for
 * the same player however many times the scanner walks over it.
 */
@GenerateNoArgConstructor
data class HousePoints(
    val goldId: String,
    val discordId: String,
    val houseId: Int,
    val season: String,
    override val played: Int,
    override val goldOpponent: Int,
    override val rivalHouse: Int,
    override val longGame: Int,
    override val victory: Int,
    override val evenGame: Int,
    override val ranked: Int,
    /**
     * When the scanner scored this row. Nothing reads it yet: with no anti-farming cap in this delivery, it plus
     * [goldId] is what makes a cap computable after the fact, mid-season, without a migration.
     */
    val scoredAt: Date? = null
) : HousePointsBreakdown
