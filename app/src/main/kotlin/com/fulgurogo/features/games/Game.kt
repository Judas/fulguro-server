package com.fulgurogo.features.games

import com.fulgurogo.Config
import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.NoArg
import com.fulgurogo.utilities.toRankInt
import java.util.*

@NoArg
open class Game(
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
    val blackPlayerRatingGain: Double? = null,
    val blackCurrentRating: Double? = null,
    val blackCurrentDeviation: Double? = null,
    val blackCurrentVolatility: Double? = null,
    val blackCurrentTierRank: Int? = null,
    val blackCurrentTierName: String? = null,
    val blackHistoricalRating: Double? = null,
    val blackHistoricalDeviation: Double? = null,
    val blackHistoricalVolatility: Double? = null,
    val blackHistoricalTierRank: Int? = null,
    val blackHistoricalTierName: String? = null,

    val whitePlayerName: String? = null,
    val whitePlayerAvatar: String? = null,
    val whitePlayerDiscordId: String? = null,
    val whitePlayerServerId: String,
    val whitePlayerPseudo: String? = null,
    val whitePlayerRank: String? = null,
    val whitePlayerWon: Boolean? = null,
    val whitePlayerRatingGain: Double? = null,
    val whiteCurrentRating: Double? = null,
    val whiteCurrentDeviation: Double? = null,
    val whiteCurrentVolatility: Double? = null,
    val whiteCurrentTierRank: Int? = null,
    val whiteCurrentTierName: String? = null,
    val whiteHistoricalRating: Double? = null,
    val whiteHistoricalDeviation: Double? = null,
    val whiteHistoricalVolatility: Double? = null,
    val whiteHistoricalTierRank: Int? = null,
    val whiteHistoricalTierName: String? = null,

    val handicap: Int? = null,
    val komi: Double? = null,
    val longGame: Boolean? = null,
    val finished: Boolean,
    val sgf: String? = null
) {
    // id format is serverName/gameId (OGS/1234567)
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

    fun gameLink(black: Boolean): String = when (server) {
        UserAccount.OGS.fullName -> "${Config.Ogs.WEBSITE_URL}/game/${gameServerId()}"
        UserAccount.FOX.fullName -> "${Config.Fox.GAME_LINK}${gameServerId()}"
        UserAccount.KGS.fullName -> {
            val pseudo = if (black) blackPlayerPseudo else whitePlayerPseudo
            val calendar = Calendar.getInstance()
            calendar.time = date
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1 // Java months start at 0...
            "${Config.Kgs.ARCHIVES_URL}?user=$pseudo&year=$year&month=$month"
        }

        else -> ""
    }

    fun toGlickoGame(black: Boolean): Glickotlin.Game? {
        val playerDiscordId = if (black) blackPlayerDiscordId else whitePlayerDiscordId
        val opponentDiscordId = if (black) whitePlayerDiscordId else blackPlayerDiscordId
        if (playerDiscordId == null || opponentDiscordId == null) return null
        val mainPlayerWon = if (black) blackPlayerWon else whitePlayerWon

        // Create opponent player object
        val opponentPlayer = DatabaseAccessor.ladderRatingAt(opponentDiscordId, date)
            ?.let { Glickotlin.Player(it.rating, it.deviation, it.volatility) }
            ?: DatabaseAccessor.ladderPlayer(User(opponentDiscordId))
                ?.let { Glickotlin.Player(it.rating, it.deviation, it.volatility) }
            ?: Glickotlin.Player(INITIAL_RATING, INITIAL_DEVIATION, INITIAL_VOLATILITY)

        return Glickotlin.Game(
            opponentPlayer,
            when (mainPlayerWon) {
                true -> Glickotlin.GameResult.VICTORY
                false -> Glickotlin.GameResult.DEFEAT
                null -> Glickotlin.GameResult.DRAW
            }
        )
    }
}
