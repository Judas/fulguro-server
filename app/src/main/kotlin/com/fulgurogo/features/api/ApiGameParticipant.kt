package com.fulgurogo.features.api

import com.fulgurogo.features.games.Game
import com.fulgurogo.utilities.InvalidUserException
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

data class ApiGameParticipant(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val serverId: String? = null,
    val serverPseudo: String? = null,
    val currentRating: ApiRating,
    val historicalRating: ApiRating,
    val ratingGain: String,
    val winner: Boolean
) {
    companion object {
        fun from(game: Game, black: Boolean): ApiGameParticipant {
            val discordId = if (black) game.blackPlayerDiscordId else game.whitePlayerDiscordId
            val userName = (if (black) game.blackPlayerName else game.whitePlayerName) ?: "Joueur désinscrit"
            val userAvatar = (if (black) game.blackPlayerAvatar else game.whitePlayerAvatar)
                ?: "https://i.pinimg.com/736x/2f/21/a4/2f21a4e243001c1385d64080273ca553.jpg"
            val serverId = if (black) game.blackPlayerServerId else game.whitePlayerServerId
            val serverPseudo = if (black) game.blackPlayerPseudo else game.whitePlayerPseudo
            val currentRating = ApiRating.from(game, black, true)
            val historicalRating = ApiRating.from(game, black, false)
            val winner = (black && game.blackPlayerWon == true) || (!black && game.whitePlayerWon == true)
            val gain = if (black) game.blackPlayerRatingGain else game.whitePlayerRatingGain
            val ratingGain = gain?.let { g ->
                when {
                    g > 0 -> "+${gain.absoluteValue.roundToInt()}"
                    g < 0 -> "-${gain.absoluteValue.roundToInt()}"
                    else -> "="
                }
            } ?: "="

            return discordId?.let {
                ApiGameParticipant(
                    discordId = it,
                    name = userName,
                    avatar = userAvatar,
                    serverId = serverId,
                    serverPseudo = serverPseudo,
                    currentRating = currentRating,
                    historicalRating = historicalRating,
                    winner = winner,
                    ratingGain = ratingGain
                )
            } ?: throw InvalidUserException
        }
    }
}
