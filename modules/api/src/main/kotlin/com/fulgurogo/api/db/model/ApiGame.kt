package com.fulgurogo.api.db.model

import java.util.*

data class ApiGame(
    val goldId: String,
    val date: Date,
    val black: ApiGameParticipant,
    val white: ApiGameParticipant,
    val result: String,
    val sgf: String,
    val gameLink: String
)

data class ApiGameParticipant(
    val discordId: String,
    val discordName: String,
    val discordAvatar: String,
    val rating: Double,
    val tierRank: Int,
    val tierName: String
)
