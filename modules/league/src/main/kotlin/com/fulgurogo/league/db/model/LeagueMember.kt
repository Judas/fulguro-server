package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * A player's place in an academy, for one season. The season is part of the key, which is what empties the academies for
 * free on 1 September: the new season simply has no rows.
 *
 * The house is not here. `house_members` is its source, and duplicating it would create a second truth; it is frozen on
 * the match instead, at the moment it actually matters.
 */
@GenerateNoArgConstructor
data class LeagueMember(
    val season: String,
    val discordId: String,
    /**
     * When the player first joined this season's academy. **Not** restamped when they leave and come back, so that the
     * renown they had already earned keeps its context.
     */
    val joined: Date,
    /**
     * Whether they are in the running. Only the draw reads it.
     *
     * False rather than a deleted row, so a player who leaves keeps their matches and their renown visible and can come
     * back. It is also set by the tick that reconciles the three automatic exits — no OGS account, no house, gone from
     * Discord.
     */
    val active: Boolean = true,
    /** When they last left. Kept after a return, as the trace of the departure rather than of the current state. */
    val leftSince: Date? = null
)
