package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class ApiDbGame(
    val goldId: String,
    val date: Date,
    val result: String,
    val sgf: String,
    val blackDiscordId: String,
    val blackDiscordName: String,
    val blackDiscordAvatar: String,
    val blackRating: Double,
    val blackTierRank: Int,
    val blackTierName: String,
    val whiteDiscordId: String,
    val whiteDiscordName: String,
    val whiteDiscordAvatar: String,
    val whiteRating: Double,
    val whiteTierRank: Int,
    val whiteTierName: String
) {
    fun toApiGame() = ApiGame(
        goldId = goldId,
        date = date,
        result = result,
        sgf = sgf,
        gameLink = "TODO",
        black = ApiGameParticipant(
            discordId = blackDiscordId,
            discordName = blackDiscordName,
            discordAvatar = blackDiscordAvatar,
            rating = blackRating,
            tierRank = blackTierRank,
            tierName = blackTierName
        ),
        white = ApiGameParticipant(
            discordId = whiteDiscordId,
            discordName = whiteDiscordName,
            discordAvatar = whiteDiscordAvatar,
            rating = whiteRating,
            tierRank = whiteTierRank,
            tierName = whiteTierName
        )
    )
}
