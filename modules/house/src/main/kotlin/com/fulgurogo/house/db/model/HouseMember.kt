package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * A player's membership. One house at most per player, so there is at most one row per Discord id, and leaving a house
 * is deleting the row.
 */
@GenerateNoArgConstructor
data class HouseMember(
    val discordId: String,
    val houseId: Int,
    /**
     * When the player joined this house. The scanner only credits games dated at or after it, which is what stops a
     * player who joins in November from earning points on the October games still inside the 32-day window.
     */
    val joined: Date,
    /**
     * What the player asked for next season: null, `STAY`, `CHANGE` or `LEAVE`. Recorded as an intention during the
     * summer and applied — then cleared — when the next season opens, so reading it outside the vacation period says
     * nothing about the current membership.
     */
    val pendingAction: String? = null,
    /**
     * The house a `CHANGE` names, and null for every other intention: the player picks their next house themselves,
     * there is no draw left to make one up. Written together with [pendingAction] and cleared with it.
     *
     * Null on a `CHANGE` therefore means the target is unknowable, not "pick one for me" — the only way to get there is
     * a row recorded by a jar older than the column. The season opening leaves such a member where they are.
     */
    val pendingHouseId: Int? = null
)
