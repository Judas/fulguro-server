package com.fulgurogo.features.api

import com.fulgurogo.common.Config
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.Game
import com.fulgurogo.features.ladder.Rating
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.InvalidUserException

data class ApiGameParticipant(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val serverId: String? = null,
    val serverPseudo: String? = null,
    val rating: Rating? = null,
    val winner: Boolean
) {
    companion object {
        fun from(game: Game, black: Boolean): ApiGameParticipant {
            val discordId = if (black) game.blackPlayerDiscordId else game.whitePlayerDiscordId
            val userName = (if (black) game.blackPlayerName else game.whitePlayerName) ?: "Joueur désinscrit"
            val userAvatar = (if (black) game.blackPlayerAvatar else game.whitePlayerAvatar)
                ?: Config.get("ladder.default.avatar")
            val serverId = if (black) game.blackPlayerServerId else game.whitePlayerServerId
            val serverPseudo = if (black) game.blackPlayerPseudo else game.whitePlayerPseudo
            val winner = (black && game.blackPlayerWon == true) || (!black && game.whitePlayerWon == true)

            var rating: Rating? = null
            discordId?.let { id ->
                DatabaseAccessor.user(UserAccount.DISCORD, id)?.rating?.let {
                    rating = Rating(it, DatabaseAccessor.tierForRating(it)!!)
                }
            }

            return discordId?.let {
                ApiGameParticipant(
                    discordId = it,
                    name = userName,
                    avatar = userAvatar,
                    serverId = serverId,
                    serverPseudo = serverPseudo,
                    rating = rating,
                    winner = winner
                )
            } ?: throw InvalidUserException
        }
    }
}
