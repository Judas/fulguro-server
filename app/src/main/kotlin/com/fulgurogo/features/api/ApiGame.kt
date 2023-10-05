package com.fulgurogo.features.api

import com.fulgurogo.features.games.Game
import java.text.SimpleDateFormat

data class ApiGame(
    val id: String,
    val date: String,
    val server: String,

    val gameLink: String? = null,
    val sgf: String? = null,

    val handicap: Int? = null,
    val komi: Double? = null,
    val longGame: Boolean? = null,
    val finished: Boolean,

    val black: ApiGameParticipant,
    val white: ApiGameParticipant
) {
    companion object {
        fun from(game: Game, black: Boolean = false): ApiGame {
            return ApiGame(
                id = game.id,
                date = SimpleDateFormat("d MMM").format(game.date),
                server = game.server,
                gameLink = game.gameLink(black),
                sgf = game.sgf,
                handicap = game.handicap,
                komi = game.komi,
                longGame = game.longGame,
                finished = game.finished,
                black = ApiGameParticipant.from(game, true),
                white = ApiGameParticipant.from(game, false)
            )
        }
    }
}
