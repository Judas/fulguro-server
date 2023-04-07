package com.fulgurogo.features.games

import com.fulgurogo.Config
import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.toRankInt
import java.util.*

data class Game(
    val id: String,
    val date: Date,
    val server: String,

    val blackPlayerDiscordId: String? = null,
    val blackPlayerServerId: String,
    val blackPlayerPseudo: String? = null,
    val blackPlayerRank: String? = null,
    val blackPlayerWon: Boolean? = null,
    val blackPlayerRatingGain: Double? = null,

    val whitePlayerDiscordId: String? = null,
    val whitePlayerServerId: String,
    val whitePlayerPseudo: String? = null,
    val whitePlayerRank: String? = null,
    val whitePlayerWon: Boolean? = null,
    val whitePlayerRatingGain: Double? = null,

    val handicap: Int? = null,
    val komi: Double? = null,
    val longGame: Boolean? = null,
    val finished: Boolean,
    val tags: String? = null
) {
    // id format is serverName/gameId (OGS/1234567)
    fun gameServerId(): String = id.split("_").last()

    fun hasStandardHandicap(): Boolean = komi != null && handicap != null
            && handicap <= 9 && ((handicap == 0 && komi >= 6) || komi == 0.5)

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

    fun sgfLink(): String = when (server) {
        UserAccount.OGS.fullName -> "${Config.Ogs.API_URL}/games/${gameServerId()}/sgf"
        UserAccount.FOX.fullName -> "${Config.Fox.API_URL}/${Config.Fox.GAME_SGF}${gameServerId()}"
        UserAccount.KGS.fullName -> {
            if (blackPlayerDiscordId == null || whitePlayerDiscordId == null) gameLink(true)
            else {
                val calendar = Calendar.getInstance()
                calendar.time = date
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1 // Java months start at 0...
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val occurrences =
                    DatabaseAccessor.countDailyGamesBetween(blackPlayerDiscordId, whitePlayerDiscordId, date)
                        .let { if (it < 2) "" else "-$it" }
                "${Config.Kgs.GAME_LINK}/$year/$month/$day/$whitePlayerPseudo-$blackPlayerPseudo$occurrences.sgf"
            }
        }

        else -> ""
    }

    fun opponentLink(black: Boolean): String {
        val opponentPseudo = if (black) whitePlayerPseudo else blackPlayerPseudo
        val opponentRank = if (black) whitePlayerRank else blackPlayerRank
        val opponentServerId = if (black) whitePlayerServerId else blackPlayerServerId
        return when (server) {
            UserAccount.OGS.fullName -> "[$opponentPseudo ($opponentRank)](${Config.Ogs.WEBSITE_URL}/player/$opponentServerId)"
            UserAccount.KGS.fullName -> "[$opponentPseudo ($opponentRank)](${Config.Kgs.GRAPH_URL}?user=$opponentPseudo)"
            UserAccount.FOX.fullName -> "$opponentPseudo ($opponentRank)"
            else -> ""
        }
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
