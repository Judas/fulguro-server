package com.fulgurogo.features.ladder

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanListener
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.kgs.KgsClient
import com.fulgurogo.features.user.ogs.OgsClient
import com.fulgurogo.features.user.ogs.OgsUser
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import java.time.ZonedDateTime

class LadderService : GameScanListener {
    override fun onScanStarted() {
        log(INFO, "onScanStarted")
        handleAllUsers { user ->
            // Create ladder player
            val ladderPlayer = DatabaseAccessor.ladderPlayer(user)
            if (ladderPlayer == null || !ladderPlayer.ranked) {
                // Player not created or not ranked yet => (re)compute initial ranking
                var kgsPlayerRating: Glickotlin.Player? = null
                var ogsPlayerRating: Glickotlin.Player? = null

                (UserAccount.KGS.client as KgsClient).user(user)?.let { kgsUser ->
                    // Only count KGS if rank is stable (no ?), and offset by -1 stone
                    if (kgsUser.hasStableRank()) kgsPlayerRating = Glickotlin.Player(
                        kgsUser.rank.toRating(-1),
                        INITIAL_DEVIATION,
                        INITIAL_VOLATILITY
                    )
                }

                (UserAccount.OGS.client as OgsClient).user(user)?.let { ogsUser ->
                    val rating: OgsUser.Rating = ogsUser.fullRatings.live19
                    ogsPlayerRating = Glickotlin.Player(rating.rating, INITIAL_DEVIATION, INITIAL_VOLATILITY)
                }

                val player = kgsPlayerRating.averageWith(ogsPlayerRating)

                if (ladderPlayer == null) DatabaseAccessor.createLadderPlayer(user.discordId, player)
                else DatabaseAccessor.updateLadderPlayerInitialRating(user.discordId, player)
            }
        }
    }

    override fun onScanFinished() {
        log(INFO, "onScanFinished")

        applyRatingAlgorithm()
        DatabaseAccessor.cleanLadderRatings()
    }

    private fun applyRatingAlgorithm() = handleAllLadderPlayers { ladderPlayer ->
        // Compute date interval for games
        val now = ZonedDateTime.now(DATE_ZONE)
        val from = ladderPlayer.ratingDate ?: now.toStartOfMonth().toDate()
        val to = now.toDate()

        // Fetch ladder games in interval
        val ladderGames = DatabaseAccessor.ladderGamesFor(ladderPlayer.discordId, from, to).sortedBy { it.date }

        // If player is not ranked yet and hasn't played in the interval, no ladder evolution
        if (ladderGames.isNotEmpty()) {
            // Update player ratings using his/her games
            log(
                INFO,
                "Applying Glicko algorithm to ${ladderGames.size} games for user ${ladderPlayer.discordId}"
            )

            ladderGames.forEach { game ->
                val black = ladderPlayer.discordId == game.blackPlayerDiscordId
                var tmpPlayer = ladderPlayer.clone()
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
            // For players without games on whole day, apply glicko algo only once a day
            val noGames = DatabaseAccessor
                .ladderGamesFor(ladderPlayer.discordId, now.minusDays(1).toDate(), to)
                .isEmpty()
            if (noGames) {
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
            } else log(INFO, "Skipping algorithm, scan empty but ranked player has recent games.")
        } else log(INFO, "Skipping algorithm, no games (player unranked or not good time to update empty).")
    }
}
