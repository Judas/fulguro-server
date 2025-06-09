package com.fulgurogo.fox

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.fox.FoxModule.TAG
import com.fulgurogo.fox.api.FoxApiClient
import com.fulgurogo.fox.api.model.FoxApiGame
import com.fulgurogo.fox.api.model.FoxApiPlayerRating
import com.fulgurogo.fox.api.model.FoxGameRecord
import com.fulgurogo.fox.db.FoxDatabaseAccessor
import com.fulgurogo.fox.db.model.FoxGame
import com.fulgurogo.fox.db.model.FoxUserInfo
import com.google.gson.reflect.TypeToken
import java.time.ZonedDateTime
import java.util.*

class FoxService : PeriodicFlowService(0, 60) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        FoxDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Get user profile
                val rating = fetchPlayerRating(stale)
                if (rating == null) {
                    FoxDatabaseAccessor.markAsError(stale)
                } else {
                    FoxDatabaseAccessor.updateUser(
                        FoxUserInfo(
                            discordId = stale.discordId,
                            foxId = rating.id,
                            foxName = rating.nick,
                            foxRank = rating.rank.lowercase(),
                            updated = Date(),
                            error = false
                        )
                    )
                }

                // Add games in DB
                val games = fetchPlayerGames(stale)
                games.forEach { game ->
                    val oldGame = FoxDatabaseAccessor.game(game)
                    val blackDiscordUser = FoxDatabaseAccessor.userById(game.blackId)
                    val whiteDiscordUser = FoxDatabaseAccessor.userById(game.whiteId)
                    val isGoldGame = blackDiscordUser != null && whiteDiscordUser != null
                    if (oldGame == null) {
                        FoxDatabaseAccessor.addGame(game)
                        if (isGoldGame) notifyGame(game)
                    } else if (!oldGame.isFinished() && game.isFinished()) {
                        // Game previously saved as "unfinished" is now finished
                        FoxDatabaseAccessor.finishGame(game)
                        if (isGoldGame) notifyGame(game)
                    }
                }
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                FoxDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }

    private fun fetchPlayerRating(stale: FoxUserInfo): FoxApiPlayerRating? {
        val route = "${Config.get("fox.api.url")}/players?nick=${stale.foxName}"
        return FoxApiClient.get(route, FoxApiPlayerRating::class.java)
    }

    private fun fetchPlayerGames(stale: FoxUserInfo): List<FoxGame> {
        // Get last games
        val route = "${Config.get("fox.api.url")}/players/${stale.foxId}/games"
        val games: List<FoxApiGame> = FoxApiClient.get(route, object : TypeToken<List<FoxApiGame>>() {})

        return games.mapNotNull {
            // Skip bot games
            if (it.black.isBot() || it.white.isBot()) return@mapNotNull null

            // Skip weird result
            val result = it.result()
            if (result == null) return@mapNotNull null

            // Date
            val date = it.date()

            // Fetch SGF
            val sgf = fetchSgf(it)

            // Check long game from SGF
            val timeLimit = getSgfProperty(sgf, "TM")?.toIntOrNull() ?: 0

            FoxGame(
                goldId = it.goldId(),
                id = it.id,
                date = date,
                blackId = it.black.id,
                blackName = it.black.nick,
                blackRank = it.black.rank.lowercase(),
                whiteId = it.white.id,
                whiteName = it.white.nick,
                whiteRank = it.white.rank.lowercase(),
                size = it.settings.size,
                komi = it.settings.komi * 2, // Fox uses half komi...
                handicap = it.settings.handicap,
                ranked = false, // Everything is ranked on FOX but we exclude it from FGC validity
                longGame = timeLimit >= 1200,
                result = result,
                sgf = sgf
            )
        }
    }

    private fun fetchSgf(game: FoxApiGame): String = try {
        val route = "${Config.get("fox.api.url")}/games/${game.id}"
        FoxApiClient.get(route, FoxGameRecord::class.java).sgf
    } catch (_: Exception) {
        ""
    }

    private fun getSgfProperty(sgf: String, key: String): String? =
        if (sgf.contains("$key[")) {
            val keyIndex = sgf.indexOf("$key[")
            val suffix = sgf.substring(keyIndex + key.length + 1)
            suffix.substring(0, suffix.indexOf("]"))
        } else null

    private fun notifyGame(game: FoxGame) {
        // Do not notify if game started more than 4h ago
        val now = ZonedDateTime.now(DATE_ZONE)
        if (now.minusHours(4).toDate().after(game.date)) return

        val title = ":popcorn: Partie ${if (game.isFinished()) "terminée" else "en cours"} sur FOX !"
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = game.description(),
            title = title,
            imageUrl = if (game.isFinished()) "" else Config.get("gold.ongoing.game.thumbnail")
        )
    }
}
