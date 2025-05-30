package com.fulgurogo.common.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import com.fulgurogo.common.utilities.toRankInt
import java.util.*

@GenerateNoArgConstructor
class Game(
    val id: String,
    val date: Date,
    val server: String,

    val blackPlayerName: String? = null,
    val blackPlayerAvatar: String? = null,
    val blackPlayerDiscordId: String? = null,
    val blackPlayerServerId: String,
    val blackPlayerPseudo: String? = null,
    val blackPlayerRank: String? = null,
    val blackPlayerWon: Boolean? = null,

    val whitePlayerName: String? = null,
    val whitePlayerAvatar: String? = null,
    val whitePlayerDiscordId: String? = null,
    val whitePlayerServerId: String,
    val whitePlayerPseudo: String? = null,
    val whitePlayerRank: String? = null,
    val whitePlayerWon: Boolean? = null,

    val handicap: Int? = null,
    val komi: Double? = null,
    val longGame: Boolean? = null,
    val finished: Boolean,
    val sgf: String? = null,
    val gameLink: String? = null
) {
    // id format is serverName_gameId (OGS_1234567)
    fun gameServerId(): String = id.split("_").last()

    fun hasNoHandicap(): Boolean = komi != null && handicap != null
            && handicap == 0 && komi in 6.0..9.0

    fun rankGap(black: Boolean): Int {
        val playerRank = if (black) whitePlayerRank else blackPlayerRank
        val opponentRank = if (black) whitePlayerRank else blackPlayerRank
        return when {
            playerRank.isNullOrBlank() -> 0
            opponentRank.isNullOrBlank() -> 0
            playerRank.contains("?") -> 0
            opponentRank.contains("?") -> 0
            else -> playerRank.toRankInt() - opponentRank.toRankInt()
        }
    }
}
