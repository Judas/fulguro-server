package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * The OGS side of a player, for life and with no season: registering a member with OGS cannot be undone, and a player
 * stays in the OGS league after leaving their academy.
 *
 * There is deliberately no `memberId` field. The OGS member id is derived — `LeagueMemberId.of(discordId)` — and one
 * function does it. A second implementation of the same hash would be a second identity for the same player, and it
 * would only show up when a match was created under it.
 */
@GenerateNoArgConstructor
data class LeaguePlayer(
    val discordId: String,
    /**
     * When `PUT member/{id}` succeeded for this player. Null is the work queue: the row is written when they join, and
     * the OGS call is left to the tick so that joining cannot fail because OGS is momentarily down.
     */
    val ogsRegistered: Date? = null
)
