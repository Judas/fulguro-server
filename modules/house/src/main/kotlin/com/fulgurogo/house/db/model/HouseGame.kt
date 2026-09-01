package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * A finished game as the `house_games` view flattens it: one row per game, whichever platform stored it, with both
 * players reduced to their Discord id.
 *
 * A null [blackDiscordId] or [whiteDiscordId] means that side is unknown to the server — nobody linked that account.
 * That null is load-bearing: it is what tells the `gold_opponent` bonus apart from no bonus at all.
 */
@GenerateNoArgConstructor
data class HouseGame(
    val goldId: String,
    val date: Date,
    /** `black`, `white` or `jigo`. The view filters `unfinished` out, so it never reaches here. */
    val result: String,
    val ranked: Boolean,
    val longGame: Boolean,
    /** 0 on an even game, which is what the `even_game` bonus keys on — not a drawn result. */
    val handicap: Int,
    /**
     * The side of the board, 19, 13 or 9 — what the scale divides by. Any other size is filtered out of the scanner's
     * selection, so it never reaches the scale at all.
     */
    val size: Int,
    val blackDiscordId: String? = null,
    val whiteDiscordId: String? = null
)
