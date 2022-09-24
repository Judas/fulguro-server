package com.fulgurogo.features.ladder.api

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.Game
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.FORMATTER
import com.fulgurogo.utilities.InvalidUserException
import com.fulgurogo.utilities.rankToString
import com.fulgurogo.utilities.toRank
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class ApiLadderPlayerGameParticipant(
    val discordId: String,
    val name: String?,
    val avatar: String?,
    val serverId: String?,
    val serverPseudo: String?,
    val currentRating: String?,
    val currentRank: String?,
    val historicalRating: String?,
    val historicalRank: String?,
    val black: Boolean?,
    val winner: Boolean?,
    val ratingGain: String?
) {
    companion object {
        fun from(game: Game, isMain: Boolean): ApiLadderPlayerGameParticipant {
            val discordId = if (isMain) game.mainPlayerId else game.opponentId
            discordId?.let {
                val user = DatabaseAccessor.ensureUser(it)
                val ladderPlayer = DatabaseAccessor.ladderPlayer(user)
                val currentRating = ladderPlayer?.rating
                val historicalRating = DatabaseAccessor.ladderRatingAt(it, game.date)?.rating ?: currentRating
                val serverId = when (game.server) {
                    UserAccount.OGS.fullName -> user.ogsId
                    UserAccount.KGS.fullName -> user.kgsId
                    else -> ""
                }
                val serverPseudo = when (game.server) {
                    UserAccount.OGS.fullName -> user.ogsPseudo
                    UserAccount.KGS.fullName -> user.kgsPseudo
                    else -> ""
                }
                return ApiLadderPlayerGameParticipant(
                    discordId = it,
                    name = user.name ?: "Joueur désinscrit",
                    avatar = user.avatar ?: "https://i.pinimg.com/736x/2f/21/a4/2f21a4e243001c1385d64080273ca553.jpg",
                    serverId = serverId,
                    serverPseudo = serverPseudo,
                    currentRating = currentRating?.let { "${currentRating.roundToInt()}" } ?: "?",
                    currentRank = currentRating?.let {
                        "${currentRating.toRank().rankToString(false)}${if (!ladderPlayer.ranked) "?" else ""}"
                    } ?: "?",
                    historicalRating = historicalRating?.let { "${historicalRating.roundToInt()}" } ?: "?",
                    historicalRank = historicalRating?.let {
                        "${
                            historicalRating.toRank().rankToString(false)
                        }${if (ladderPlayer?.ranked == false) "?" else ""}"
                    } ?: "?",
                    black = isMain == game.mainPlayerIsBlack,
                    winner = isMain == game.mainPlayerWon,
                    ratingGain = if (isMain && game.mainPlayerRatingGain != null && historicalRating != null) {
                        val sign = if (game.mainPlayerRatingGain > 0) "+" else "-"
                        val value = FORMATTER.format(floor(abs(game.mainPlayerRatingGain) * 100) / 100)
                        val rankOffset =
                            (historicalRating + game.mainPlayerRatingGain).toRank() - historicalRating.toRank()
                        val rankValue = FORMATTER.format(floor(abs(rankOffset) * 100) / 100)
                        "$sign$value ($sign${rankValue}k)"
                    } else null
                )
            } ?: throw InvalidUserException
        }
    }
}
