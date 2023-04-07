package com.fulgurogo.features.exam

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.Game

data class ExamPoints(
    var participation: Int = 0,
    var community: Int = 0,
    var patience: Int = 0,
    var victory: Int = 0,
    var refinement: Int = 0,
    var performance: Int = 0,
    var achievement: Int = 0
) {
    companion object {
        fun fromGame(game: Game, black: Boolean): ExamPoints? {
            // Quick exit if main player is not exam player (random server player)
            val mainPlayerId = (if (black) game.blackPlayerDiscordId else game.whitePlayerDiscordId) ?: return null
            val opponentId = if (black) game.whitePlayerDiscordId else game.blackPlayerDiscordId
            val community = opponentId != null
            val victory = if (black) game.blackPlayerWon else game.whitePlayerWon

            val points = ExamPoints()
            points.participation += 1
            if (community) {
                points.community += 2
                opponentId?.let {
                    val opponentIsHunter = DatabaseAccessor.examPlayer(it)?.hunter ?: false
                    if (victory == true && opponentIsHunter) points.achievement += 2
                }
            }
            if (game.longGame == true) points.patience += 2

            if (victory == true) {
                points.victory += 2
                if (game.hasNoHandicap()) {
                    points.refinement += 1
                    if (game.rankGap(black) >= 2) points.performance += 1
                }
            }
            return points
        }
    }
}
