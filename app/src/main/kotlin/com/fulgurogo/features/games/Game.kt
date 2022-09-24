package com.fulgurogo.features.games

import com.fulgurogo.Config
import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.ladder.LadderRating
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.toRank
import com.fulgurogo.utilities.toRankInt
import com.fulgurogo.utilities.toRating
import java.util.*

data class Game(
    val id: Int,
    val date: Date,
    val server: String,
    val serverId: String,
    val mainPlayerId: String,
    val mainPlayerServerRank: String? = null,
    val opponentId: String? = null,
    val opponentServerId: String? = null,
    val opponentServerPseudo: String? = null,
    val opponentServerRank: String? = null,
    val mainPlayerIsBlack: Boolean? = null,
    val mainPlayerWon: Boolean? = null,
    val mainPlayerRatingGain: Double? = null,
    val handicap: Int? = null,
    val komi: Double? = null,
    val longGame: Boolean? = null,
    val finished: Boolean,
) {
    // Sen game on KGS : komi 0.5 + handicap 0
    // Sen game on OGS : komi 0.5 + handicap 1
    fun hasStandardHandicap(): Boolean = komi != null && handicap != null
            && handicap <= 9 && ((handicap == 0 && komi >= 6) || komi == 0.5)

    private fun standardHandicap(): Int = if (hasStandardHandicap() && handicap != null) {
        if (mainPlayerIsBlack == true) -handicap else handicap
    } else throw IllegalStateException("Invalid handicap settings")

    fun toGlickoGame(): Glickotlin.Game? {
        if (opponentId == null) return null

        // Create opponent player object with translated handicap
        val opponentLadderRating = DatabaseAccessor.ladderRatingAt(opponentId, date)
            ?: DatabaseAccessor.ladderPlayer(User(opponentId))
                ?.let { LadderRating(it.discordId, date, it.rating, it.deviation, it.volatility) }
            ?: LadderRating(opponentId, date, INITIAL_RATING, INITIAL_DEVIATION, INITIAL_VOLATILITY)

        val opponentRating = if (handicap != 0)
            (opponentLadderRating.rating.toRank() + standardHandicap().toDouble()).toRating()
        else opponentLadderRating.rating

        val opponentPlayer = Glickotlin.Player(
            opponentRating,
            opponentLadderRating.deviation,
            opponentLadderRating.volatility
        )

        return Glickotlin.Game(
            opponentPlayer,
            when (mainPlayerWon) {
                true -> Glickotlin.GameResult.VICTORY
                false -> Glickotlin.GameResult.DEFEAT
                null -> Glickotlin.GameResult.DRAW
            }
        )
    }

    fun rankGap(): Int {
        if (mainPlayerServerRank.isNullOrBlank() || opponentServerRank.isNullOrBlank()) return 0
        if (mainPlayerServerRank.contains("?") || opponentServerRank.contains("?")) return 0
        return mainPlayerServerRank.toRankInt() - opponentServerRank.toRankInt()
    }

    fun gameLink(): String = when (server) {
        UserAccount.OGS.fullName -> "${Config.Ogs.WEBSITE_URL}/game/$serverId"
        UserAccount.FOX.fullName -> "${Config.Fox.GAME_LINK}$serverId"
        UserAccount.KGS.fullName -> {
            val pseudo = DatabaseAccessor.ensureUser(mainPlayerId).kgsPseudo
            val calendar = Calendar.getInstance()
            calendar.time = date
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1 // Java months start at 0...
            "${Config.Kgs.ARCHIVES_URL}?user=$pseudo&year=$year&month=$month"
        }

        else -> ""
    }

    fun sgfLink(): String = when (server) {
        UserAccount.OGS.fullName -> "${Config.Ogs.API_URL}/games/${serverId}/sgf"
        UserAccount.FOX.fullName -> "${Config.Fox.API_URL}/${Config.Fox.GAME_SGF}$serverId"
        UserAccount.KGS.fullName ->
            if (opponentId == null) gameLink()
            else {
                val calendar = Calendar.getInstance()
                calendar.time = date
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1 // Java months start at 0...
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val mainPlayer = DatabaseAccessor.ensureUser(mainPlayerId)
                val opponent = DatabaseAccessor.ensureUser(opponentId)
                val black = if (mainPlayerIsBlack == true) mainPlayer.kgsPseudo else opponent.kgsPseudo
                val white = if (mainPlayerIsBlack == true) opponent.kgsPseudo else mainPlayer.kgsPseudo
                val occurences = DatabaseAccessor.countDayGamesBetween(mainPlayerId, opponentId, date).let {
                    if (it < 2) "" else "-$it"
                }
                "${Config.Kgs.GAME_LINK}/$year/$month/$day/$white-$black$occurences.sgf"
            }

        else -> ""
    }

    fun opponentLink(): String = when (server) {
        UserAccount.OGS.fullName -> "[$opponentServerPseudo ($opponentServerRank)](${Config.Ogs.WEBSITE_URL}/player/$opponentServerId)"
        UserAccount.KGS.fullName -> "[$opponentServerPseudo ($opponentServerRank)](${Config.Kgs.GRAPH_URL}?user=$opponentServerPseudo)"
        UserAccount.FOX.fullName -> "$opponentServerPseudo ($opponentServerRank)"
        else -> ""
    }
}
