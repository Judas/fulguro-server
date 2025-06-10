package com.fulgurogo.api.db.model

data class ApiGame(
    val goldId: String,
    val date: String,
    val black: ApiGameParticipant,
    val white: ApiGameParticipant,
    val result: String,
    val sgf: String
)

data class ApiGameParticipant(
    val discordId: String,
    val discordName: String,
    val discordAvatar: String,
    val rating: Double,
    val tierRank: Int,
    val tierName: String
)
