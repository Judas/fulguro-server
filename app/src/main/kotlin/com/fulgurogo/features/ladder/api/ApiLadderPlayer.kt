package com.fulgurogo.features.ladder.api

data class ApiLadderPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val kgsId: String? = null,
    val kgsPseudo: String? = null,
    val ogsId: String? = null,
    val ogsPseudo: String? = null,
    val foxId: String? = null,
    val foxPseudo: String? = null,
    val rating: Double? = null,
    val fullRating: String? = null,
    val rank: String? = null,
    val fullRank: String? = null,
    val ranked: Boolean
)
