package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * The raw aggregate row behind a standings line, straight out of SQL and not meant to leave the accessor —
 * [LeagueStanding] is what callers get.
 *
 * [played], [won] and [lost] are counted over both colours at once, and they do not add up to the number of matches
 * drawn: a match still open, or one the settlement voided, is in none of the three.
 */
@GenerateNoArgConstructor
data class LeagueTally(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    /** Null when the player has left their house — which also makes them inactive, but the row stays in the standings. */
    val houseId: Int? = null,
    val active: Boolean = true,
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0
)
