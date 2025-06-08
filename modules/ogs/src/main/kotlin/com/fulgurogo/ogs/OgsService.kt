package com.fulgurogo.ogs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.rankToKyuDanString
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.ogs.OgsModule.TAG
import com.fulgurogo.ogs.api.OgsApiClient
import com.fulgurogo.ogs.api.model.OgsApiGame
import com.fulgurogo.ogs.api.model.OgsApiGameList
import com.fulgurogo.ogs.api.model.OgsApiPlayerRating
import com.fulgurogo.ogs.db.OgsDatabaseAccessor
import com.fulgurogo.ogs.db.model.OgsGame
import com.fulgurogo.ogs.db.model.OgsUserInfo
import java.time.ZonedDateTime
import java.util.*

class OgsService : PeriodicFlowService(0, 5) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        OgsDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Get user profile
                val rating = fetchPlayerRating(stale)
                if (rating == null) {
                    OgsDatabaseAccessor.markAsError(stale)
                } else {
                    OgsDatabaseAccessor.updateUser(
                        OgsUserInfo(
                            discordId = stale.discordId,
                            ogsId = stale.ogsId,
                            ogsName = rating.username,
                            ogsRank = rating.ranking.rankToKyuDanString(),
                            updated = Date(),
                            error = false
                        )
                    )
                }

                // Add games in DB
                val games = fetchPlayerGames(stale)
                games.forEach { game ->
                    val oldGame = OgsDatabaseAccessor.game(game)
                    val blackDiscordUser = OgsDatabaseAccessor.user(game.blackId)
                    val whiteDiscordUser = OgsDatabaseAccessor.user(game.whiteId)
                    val isGoldGame = blackDiscordUser != null && whiteDiscordUser != null
                    if (oldGame == null) {
                        OgsDatabaseAccessor.addGame(game)
                        if (isGoldGame) notifyGame(game)
                    } else if (!oldGame.isFinished() && game.isFinished()) {
                        // Game previously saved as "unfinished" is now finished
                        OgsDatabaseAccessor.finishGame(game)
                        if (isGoldGame) notifyGame(game)
                    }
                }
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                OgsDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }

    private fun fetchPlayerRating(stale: OgsUserInfo): OgsApiPlayerRating? {
        val route = "${Config.get("ogs.termination.api.url")}/player/${stale.ogsId}"
        return OgsApiClient.get(route, OgsApiPlayerRating::class.java)
    }

    private fun fetchPlayerGames(stale: OgsUserInfo): List<OgsGame> {
        // Get last 10 games
        val route = "${Config.get("ogs.api.url")}/players/${stale.ogsId}/games?ordering=-ended"
        val games = OgsApiClient.get(route, OgsApiGameList::class.java).results

        return games.mapNotNull {
            // Skip cancelled games
            if (it.annulled) return@mapNotNull null

            // Skip non-square goban
            if (it.height != it.width) return@mapNotNull null

            // Skip bot games
            if (it.players.black.isBot() || it.players.white.isBot()) return@mapNotNull null

            // Skip correspondence games
            if (it.isCorrespondence()) return@mapNotNull null

            // Skip rengo
            if (it.rengo) return@mapNotNull null

            // Skip weird result
            val result = it.result()
            if (result == null) return@mapNotNull null

            // Date => skip games older than 6h
            val date = it.date()
            val now = ZonedDateTime.now(DATE_ZONE)
            if (now.minusHours(6).toDate().after(date)) return@mapNotNull null

            // Fetch SGF
            val sgf = fetchSgf(it)

            OgsGame(
                goldId = it.goldId(),
                id = it.id,
                date = date,
                blackId = it.players.black.id,
                blackName = it.players.black.username,
                blackRank = it.players.black.ranking.rankToKyuDanString(),
                whiteId = it.players.white.id,
                whiteName = it.players.white.username,
                whiteRank = it.players.white.ranking.rankToKyuDanString(),
                size = it.width,
                komi = it.komi.toDouble(),
                handicap = it.handicap,
                ranked = it.ranked,
                longGame = it.isLongGame(),
                result = result,
                sgf = sgf
            )
        }
    }

    private fun fetchSgf(game: OgsApiGame): String = try {
        OgsApiClient.get("${Config.get("ogs.api.url")}/games/${game.id}/sgf")
    } catch (_: Exception) {
        ""
    }

    private fun notifyGame(game: OgsGame) {
        // Do not notify if game started more than 4h ago
        val now = ZonedDateTime.now(DATE_ZONE)
        if (now.minusHours(4).toDate().after(game.date)) return

        val title = ":popcorn: Partie ${if (game.isFinished()) "terminée" else "en cours"} sur OGS !"
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = game.description(),
            title = title,
            imageUrl = if (game.isFinished()) "" else Config.get("gold.ongoing.game.thumbnail")
        )
    }
}
