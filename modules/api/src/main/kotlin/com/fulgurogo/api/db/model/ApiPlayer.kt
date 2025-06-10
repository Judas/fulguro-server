package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiPlayer(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val accounts: List<ApiPlayerAccount>? = null,
    val rating: Double = 0.0,
    val tierRank: Int = 0,
    val tierName: String? = null,
    val totalRankedGames: Int = 0,
    val goldRankedGames: Int = 0,
    var games: List<ApiGame>? = null
)
