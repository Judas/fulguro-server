package com.fulgurogo.features.ladder.api

data class ApiPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,

    val rating: Double,
    val deviation: Double,
    val ranked: Boolean,
    val tierRank: Int,
    val tierName: String,
    val stable: Boolean,

    var stability: ApiStability? = null,
    var games: MutableList<ApiGame>? = null,
    var accounts: MutableList<ApiAccount>? = null
)
