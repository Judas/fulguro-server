package com.fulgurogo.ogs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.rankToKyuDanString
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.ogs.OgsModule.TAG
import com.fulgurogo.ogs.api.model.OgsApiGame
import com.fulgurogo.ogs.api.model.OgsApiGameList
import com.fulgurogo.ogs.api.model.OgsApiPlayerRating
import com.fulgurogo.ogs.db.OgsDatabaseAccessor
import com.fulgurogo.ogs.db.model.OgsGame
import com.fulgurogo.ogs.db.model.OgsUserInfo
import com.google.gson.Gson
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class OgsService : PeriodicFlowService(0, 2) {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()
    private var lastNetworkCallTime: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

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
                            error = null
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
                    if (oldGame != null && !oldGame.isFinished() && game.isFinished()) {
                        // Game previously saved as "unfinished" is now finished
                        OgsDatabaseAccessor.finishGame(game)
                        if (isGoldGame) notifyGame(game)
                    } else if (oldGame == null && !game.isFinished()) {
                        // New "ongoing" game being played now
                        // Should not happen, the real time API should have done that, but just to be sure
                        OgsDatabaseAccessor.addGame(game)
                        if (isGoldGame) notifyGame(game)
                    } else if (oldGame == null) {
                        // New game finished
                        OgsDatabaseAccessor.addGame(game)
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
        return fetch(route, OgsApiPlayerRating::class.java)
    }

    private fun fetchPlayerGames(stale: OgsUserInfo): List<OgsGame> {
        // Get last 20 games (2 pages)
        val games = mutableListOf<OgsApiGame>()

        // Fetch first page
        val route = "${Config.get("ogs.api.url")}/players/${stale.ogsId}/games?ordering=-ended"
        val gameList = fetch(route, OgsApiGameList::class.java)
        games.addAll(gameList.results)

        // Fetch second page
        if (!gameList.next.isNullOrBlank())
            games.addAll(fetch(gameList.next, OgsApiGameList::class.java).results)

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
            // Date => skip too old game
            val date = it.date()
            val now = ZonedDateTime.now(DATE_ZONE)
            if (now.minusDays(32).toDate().after(date)) return@mapNotNull null

            // Fetch SGF
            val sgf = fetchSgf(it)
            if (sgf.isBlank()) return@mapNotNull null

            OgsGame(
                id = it.id,
                date = date,
                blackId = it.players.black.id,
                blackName = it.players.black.username ?: "?",
                blackRank = it.players.black.ranking.rankToKyuDanString(),
                whiteId = it.players.white.id,
                whiteName = it.players.white.username ?: "?",
                whiteRank = it.players.white.ranking.rankToKyuDanString(),
                size = it.width,
                komi = it.komi.toDouble(),
                handicap = it.handicap,
                longGame = it.isLongGame(),
                result = result,
                sgf = sgf
            )
        }
    }

    private fun fetchSgf(game: OgsApiGame): String =
        fetch("${Config.get("ogs.api.url")}/games/${game.id}/sgf")

    private fun <T : Any> fetch(route: String, className: Class<T>): T =
        gson.fromJson(fetch(route), className)

    private fun fetch(route: String): String {
        // Delay to avoid spamming OGS API: ensure between 500ms & 1500ms free time
        val now = ZonedDateTime.now(DATE_ZONE)
        if (lastNetworkCallTime.plusSeconds(1).isAfter(now))
            Thread.sleep(500)
        lastNetworkCallTime = ZonedDateTime.now(DATE_ZONE)

        val request: Request = Request.Builder().url(route).get().build()
        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            val apiResponse = response.body!!.string()
            response.close()
            apiResponse
        } else {
            val error = Exception("GET FAILURE " + response.code)
            log(TAG, error.message!!, error)
            throw error
        }
    }

    private fun notifyGame(game: OgsGame) {
        val title = ":popcorn: Partie ${if (game.isFinished()) "terminée" else "en cours"} sur OGS !"
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = game.description(),
            title = title
        )
    }
}
