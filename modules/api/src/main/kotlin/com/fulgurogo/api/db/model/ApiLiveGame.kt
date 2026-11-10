package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/** `GET /gold/api/games/live`: the OGS games in progress with at least one member, and how fresh that list is. */
data class ApiLiveGames(
    val secondsSinceUpdate: Long,
    val games: List<ApiLiveGame>,
)

data class ApiLiveGame(
    val goldId: String,
    val ogsId: Int,
    val link: String,
    val name: String?,
    val size: Int,
    val handicap: Int?,
    val ranked: Boolean?,
    val moveNumber: Int?,
    /** Both players are members. False means one of them is an outsider. */
    val gold: Boolean,
    val black: ApiLiveGameParticipant,
    val white: ApiLiveGameParticipant,
)

/** An OGS player of a live game. [member] is null for an opponent who is not on the ladder. */
data class ApiLiveGameParticipant(
    val ogsId: Int,
    val ogsName: String?,
    val ogsRank: String?,
    val member: ApiOgsMember?,
)

/** A member as seen from their OGS id. Rating and tier are null until GoldService has rated them. */
@GenerateNoArgConstructor
data class ApiOgsMember(
    val ogsId: Int,
    val discordId: String,
    val discordName: String,
    val discordAvatar: String,
    val rating: Double?,
    val tierRank: Int?,
    val tierName: String?,
)
