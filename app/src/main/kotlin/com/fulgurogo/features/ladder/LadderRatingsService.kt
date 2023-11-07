package com.fulgurogo.features.ladder

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import java.time.ZonedDateTime

object LadderRatingsService {
    fun refresh() {
        log(INFO, "refresh")

        applyRatingAlgorithm()
        DatabaseAccessor.cleanLadderRatings()
    }

    private fun applyRatingAlgorithm() = handleAllLadderPlayers { ladderPlayer ->
        // Compute date interval for games
        val now = ZonedDateTime.now(DATE_ZONE)
        val from = ladderPlayer.ratingDate ?: now.toStartOfMonth().toDate()
        val to = now.toDate()

        // Fetch ladder games in interval
        val ladderGames = DatabaseAccessor.ladderGamesFor(ladderPlayer.discordId, from, to)
        if (ladderGames.isNotEmpty()) {
            // Update player ratings using his/her games
            log(
                INFO,
                "Applying Glicko algorithm to ${ladderGames.size} games for user ${ladderPlayer.discordId}"
            )
            var tmpPlayer = ladderPlayer.clone()
            ladderGames.forEach { game ->
                val black = ladderPlayer.discordId == game.blackPlayerDiscordId
                game.toGlickoGame(black)?.let { glickoGame ->
                    val opponentPlayer = glickoGame.opponent
                    val logicalResult = when (glickoGame.result) {
                        Glickotlin.GameResult.VICTORY ->
                            tmpPlayer.rating - tmpPlayer.deviation > opponentPlayer.rating() + opponentPlayer.deviation()

                        Glickotlin.GameResult.DEFEAT ->
                            tmpPlayer.rating + tmpPlayer.deviation < opponentPlayer.rating() - opponentPlayer.deviation()

                        Glickotlin.GameResult.DRAW -> false
                    }

                    val preRating = tmpPlayer.rating
                    val glickoPlayer = Glickotlin.Player(tmpPlayer.rating, tmpPlayer.deviation, tmpPlayer.volatility)

                    if (!logicalResult) Glickotlin.Algorithm().updateRating(glickoPlayer, listOf(glickoGame))

                    val postRating = glickoPlayer.rating()
                    DatabaseAccessor.saveGameRatingGain(game.id, black, postRating - preRating)

                    tmpPlayer = LadderPlayer(
                        ladderPlayer.discordId,
                        game.date,
                        glickoPlayer.rating(),
                        glickoPlayer.deviation(),
                        glickoPlayer.volatility(),
                        true
                    )

                    // Save updated rating in DB
                    DatabaseAccessor.updateLadderPlayer(tmpPlayer)
                    DatabaseAccessor.saveLadderRating(tmpPlayer)
                }
            }
        } else if (ladderPlayer.ranked && now.hour in 2..3) {
            // For players without games only apply glicko algo once a day (to increase deviation)
            val noGames = DatabaseAccessor
                .ladderGamesFor(ladderPlayer.discordId, now.minusDays(1).toDate(), to)
                .isEmpty()
            if (noGames) {
                log(INFO, "User has no games for 24h, applying empty Glicko algorithm.")
                val glickoPlayer =
                    Glickotlin.Player(ladderPlayer.rating, ladderPlayer.deviation, ladderPlayer.volatility)
                Glickotlin.Algorithm().updateRating(glickoPlayer, listOf())
                val updatedPlayer = LadderPlayer(
                    ladderPlayer.discordId,
                    to,
                    glickoPlayer.rating(),
                    glickoPlayer.deviation(),
                    glickoPlayer.volatility(),
                    true
                )

                // Save updated rating in DB
                DatabaseAccessor.updateLadderPlayer(updatedPlayer)
                DatabaseAccessor.saveLadderRating(updatedPlayer)
            } else log(INFO, "Skipping algorithm, scan is empty but player has played today.")
        } else log(
            INFO,
            "Skipping algorithm, either the player is unranked or it is not yet the time to applying empty algo."
        )
    }
}
