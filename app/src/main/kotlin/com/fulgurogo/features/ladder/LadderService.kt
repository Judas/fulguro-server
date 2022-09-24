package com.fulgurogo.features.ladder

import com.fulgurogo.Config
import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanListener
import com.fulgurogo.features.ladder.api.ApiLadderPlayerGame
import com.fulgurogo.features.ladder.api.ApiLadderPlayerRatings
import com.fulgurogo.features.ladder.glicko.Glickotlin
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.kgs.KgsClient
import com.fulgurogo.features.user.ogs.OgsClient
import com.fulgurogo.features.user.ogs.OgsUser
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
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
                    ogsPlayerRating = Glickotlin.Player(rating.rating, rating.deviation, rating.volatility)
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
        serveStaticResults()
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
                var tmpPlayer = ladderPlayer.clone()
                game.toGlickoGame()?.let { glickoGame ->
                    val glickoPlayer =
                        Glickotlin.Player(tmpPlayer.rating, tmpPlayer.deviation, tmpPlayer.volatility)
                    val preRating = tmpPlayer.rating
                    Glickotlin.Algorithm().updateRating(glickoPlayer, listOf(glickoGame))
                    val postRating = glickoPlayer.rating()

                    DatabaseAccessor.saveGameRatingGain(game.id, postRating - preRating)

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

    private fun serveStaticResults() {
        log(INFO, "serveStaticResults")

        val root = Config.Ladder.STATIC_FOLDER
        val gson = Gson()

        // GET /players
        log(INFO, "Writing /players file")
        val players = DatabaseAccessor.apiLadderPlayers()
        File(root, "players").writeText(gson.toJson(players))

        players.forEachIndexed { index, player ->
            log(INFO, "[${index + 1}/${players.size}] Processing user ${player.discordId}")

            val playerFolder = File(root, player.discordId)
            playerFolder.mkdir()

            // GET /{id}/profile
            log(INFO, "Writing /${player.discordId}/profile file")
            File(playerFolder, "profile").writeText(gson.toJson(player))

            // GET /{id}/games
            log(INFO, "Writing /${player.discordId}/games file")
            val now = ZonedDateTime.now(DATE_ZONE)
            val start = now.minusMonths(1).toDate()
            val end = now.toDate()
            val games = DatabaseAccessor.ladderGamesFor(player.discordId, start, end)
                .sortedByDescending { it.date }
                .map { ApiLadderPlayerGame.from(it) }
                .take(10)
            File(playerFolder, "games").writeText(gson.toJson(games))

            // GET /{id}/ratings
            log(INFO, "Writing /${player.discordId}/ratings file")
            val playerRatings = DatabaseAccessor.ladderRatingsFor(player.discordId)
                .associateBy { SimpleDateFormat("yyyy-MM-dd").format(it.date) }
                .values
                .toList()
                .let { ApiLadderPlayerRatings.from(it) }
            File(playerFolder, "ratings").writeText(gson.toJson(playerRatings))
        }

        val gameFolder = File(root, "game")
        gameFolder.mkdir()

        // Write all games
        DatabaseAccessor
            .ladderGames()
            .map { ApiLadderPlayerGame.from(it) }
            .forEach { game ->
                // GET /game/{id}
                log(INFO, "Writing /game/${game.id} file")
                File(gameFolder, game.id.toString()).writeText(gson.toJson(game))
            }

        // Delete old games
        (0 until DatabaseAccessor.oldestLadderGameId()).forEach { File(gameFolder, it.toString()).delete() }

        // Write last 20 games
        val latestGames = DatabaseAccessor
            .ladderGames()
            .filter { it.mainPlayerIsBlack == true }
            .sortedByDescending { it.date }
            .take(20)
            .map { ApiLadderPlayerGame.from(it) }
        File(root, "games").writeText(gson.toJson(latestGames))

        log(INFO, "Finished serving static results files")
    }
}
