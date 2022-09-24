package com.fulgurogo.features.ladder.api

import com.fulgurogo.features.games.Game
import java.text.SimpleDateFormat

data class ApiLadderPlayerGame(
    val id: Int,
    val date: String,
    val server: String,
    val serverId: String,
    val gameLink: String,
    val sgf: String?,
    val handicap: Int?,
    val komi: Double?,
    val mainPlayer: ApiLadderPlayerGameParticipant,
    val opponent: ApiLadderPlayerGameParticipant,
) {
    companion object {
        fun from(game: Game): ApiLadderPlayerGame = ApiLadderPlayerGame(
            id = game.id,
            date = SimpleDateFormat("d MMM").format(game.date),
            server = game.server,
            serverId = game.serverId,
            gameLink = game.gameLink(),
            sgf = game.sgfLink(),
            handicap = game.handicap,
            komi = game.komi,
            mainPlayer = ApiLadderPlayerGameParticipant.from(game, true),
            opponent = ApiLadderPlayerGameParticipant.from(game, false)
        )
    }
}
