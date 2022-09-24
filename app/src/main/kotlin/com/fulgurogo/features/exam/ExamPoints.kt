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
        fun fromGame(game: Game): ExamPoints {
            val points = ExamPoints()

            points.participation += 1

            if (game.opponentId != null) {
                points.community += 2
                val opponentIsHunter = DatabaseAccessor.examPlayer(game.opponentId)?.hunter ?: false
                if (game.mainPlayerWon == true && opponentIsHunter) points.achievement += 2
            }
            if (game.longGame == true) points.patience += 2

            if (game.mainPlayerWon == true) {
                points.victory += 2
                if (game.handicap == 0 && game.komi != null && game.komi in 6.0..9.0) {
                    points.refinement += 1
                    if (game.rankGap() >= 2) points.performance += 1
                }
            }
            return points
        }
    }
}
