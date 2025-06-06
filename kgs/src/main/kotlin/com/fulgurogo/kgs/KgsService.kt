package com.fulgurogo.kgs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.CookieManager
import java.net.CookiePolicy
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class KgsService : PeriodicFlowService(0, 2) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        KgsDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Scrap archives pages
                val games = scrapGames(stale)

                // Update user rank
                val updatedRank = games.maxByOrNull { it.date }?.let {
                    if (it.blackId == stale.kgsId) it.blackRank
                    else if (it.whiteId == stale.kgsId) it.whiteRank
                    else "?"
                } ?: "?"
                KgsDatabaseAccessor.updateUser(
                    KgsUserInfo(
                        discordId = stale.discordId,
                        kgsId = stale.kgsId,
                        kgsRank = updatedRank,
                        updated = Date(),
                        error = null
                    )
                )

                // Add games in DB
                games.forEach { game ->
                    val oldGame = KgsDatabaseAccessor.game(game)
                    val blackDiscordUser = KgsDatabaseAccessor.user(game.blackId)
                    val whiteDiscordUser = KgsDatabaseAccessor.user(game.whiteId)
                    val isGoldGame = blackDiscordUser != null && whiteDiscordUser != null
                    if (oldGame == null) {
                        KgsDatabaseAccessor.addGame(game)
                        if (isGoldGame) notifyGame(game)
                    } else if (!oldGame.isFinished() && game.isFinished()) {
                        // Game previously saved as "unfinished" is now finished
                        KgsDatabaseAccessor.finishGame(game)
                        if (isGoldGame) notifyGame(game)
                    }
                }
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                KgsDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }

    private fun scrapGames(stale: KgsUserInfo): List<KgsGame> = stale.kgsId?.let { kgsId ->
        val now = ZonedDateTime.now(DATE_ZONE)
        val lastMonth = now.minusMonths(1)

        val games = scrapMonthlyGames(stale.kgsId, now.year, now.monthValue)
        games.addAll(scrapMonthlyGames(stale.kgsId, lastMonth.year, lastMonth.monthValue))

        games
    } ?: listOf()

    private fun scrapMonthlyGames(kgsId: String?, year: Int, month: Int): MutableList<KgsGame> = try {
        val route = "${Config.get("kgs.archives.url")}?user=$kgsId&year=$year&month=$month"
        val html = Jsoup.connect(route)
            .userAgent(Config.get("user.agent"))
            .timeout(Config.get("global.read.timeout.ms").toInt())
            .get()

        // Get the tables, there might be 0 (no games at all), 1 (no games this month) or 2 (games, yay !)
        val tables = html.select("table.grid").asList()
        if (tables.size == 2) extractGamesFrom(tables[0]) else mutableListOf()
    } catch (e: IOException) {
        log(TAG, "scrapMonthlyGames FAILURE ${e.message}")
        mutableListOf()
    }

    private fun extractGamesFrom(gameTable: Element): MutableList<KgsGame> {
        val gameRows = gameTable.select("tr").asList()
        gameRows.removeFirst() // First row is header
        return gameRows.mapNotNull { row ->
            val columns = row.select("td").asList()
            if (columns.size != 7) return@mapNotNull null

            // Date => skip games older than 6h
            val dateString = columns[4].text().trim()
            val sdf = SimpleDateFormat("M/d/y h:mm a")
            sdf.timeZone = TimeZone.getTimeZone("GMT")
            val date = sdf.parse(dateString, ParsePosition(0))
            val now = ZonedDateTime.now(DATE_ZONE)
            if (now.minusHours(6).toDate().after(date)) return@mapNotNull null

            // Game result => keep unfinished games to alert new games on Discord
            val resultString = columns[6].text().trim()
            val result = when {
                resultString.contains("B+") -> "black"
                resultString.contains("W+") -> "white"
                resultString.equals("jigo", true) -> "jigo"
                resultString.equals("unfinished", true) -> "unfinished"
                else -> return@mapNotNull null
            }

            // Game type => skip wrong types
            // challenge, demonstration, review, rengo_review, teaching, simul, rengo, free, ranked, tournament
            val gameType = columns[5].text().trim()
            if (listOf("challenge", "demonstration", "review", "rengo_review", "teaching", "rengo").contains(gameType))
                return@mapNotNull null

            // SGF Link => Skip private games
            val sgfLink = columns[0].select("a").firstOrNull()?.attr("href")
            if (sgfLink.isNullOrBlank()) return@mapNotNull null

            // Fetch SGF content from link
            val sgf = fetchSgf(sgfLink)
            if (sgf.isBlank()) return@mapNotNull null

            // Goban size => Skip wrong size games
            val size = getSgfProperty(sgf, "SZ")?.toIntOrNull() ?: 0

            // Get handicap from SGF
            val handicap = getSgfProperty(sgf, "HA")?.toIntOrNull() ?: 0

            // Get komi from SGF
            val komi = getSgfProperty(sgf, "KM")?.toFloatOrNull() ?: return@mapNotNull null

            // Get time setting from SGF
            val isLongGame = (getSgfProperty(sgf, "TM")?.toIntOrNull() ?: 0) > 1200

            // Players
            val whitePlayer = columns[1].select("a").firstOrNull()?.text()?.trim().splitNameRank()
            val blackPlayer = columns[2].select("a").firstOrNull()?.text()?.trim().splitNameRank()

            KgsGame(
                goldId = "KGS_${blackPlayer.first}_${whitePlayer.first}_${date.time}",
                date = date,
                blackId = blackPlayer.first,
                blackRank = blackPlayer.second,
                whiteId = whitePlayer.first,
                whiteRank = whitePlayer.second,
                size = size,
                komi = komi,
                handicap = handicap,
                longGame = isLongGame,
                result = result,
                sgf = sgf
            )
        }.toMutableList()
    }

    private fun fetchSgf(sgfLink: String, allowRetry: Boolean = true): String {
        val request = Request.Builder()
            .url(sgfLink)
            .header("User-Agent", Config.get("user.agent"))
            .get().build()
        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            val responseBody = response.body!!.string().replace("\n", "")
            response.close()
            responseBody
        } else if (allowRetry) {
            // Retry once after delay
            Thread.sleep(1000L)
            log(TAG, "Fetching SGF ERROR: Waiting then retrying")
            fetchSgf(sgfLink, false)
        } else {
            // Failed twice
            log(TAG, "Fetching SGF FAILURE " + response.code)
            ""
        }
    }

    private fun getSgfProperty(sgf: String, key: String): String? =
        if (sgf.contains("$key[")) {
            val keyIndex = sgf.indexOf("$key[")
            val suffix = sgf.substring(keyIndex + key.length + 1)
            suffix.substring(0, suffix.indexOf("]"))
        } else null

    private fun String?.splitNameRank(): Pair<String, String> = this?.let {
        val splitted = split(" ")
        val name = splitted[0]
        val rank = when {
            splitted.size <= 1 -> "?"
            splitted[1].isBlank() -> "?"
            splitted[1].contains("?") -> "?"
            splitted[1].contains("-") -> "?"
            else -> splitted[1].replace("[", "").replace("]", "")
        }
        (name to rank)
    } ?: ("" to "?")

    private fun notifyGame(game: KgsGame) {
        // Do not notify if game started more than 4h ago
        val now = ZonedDateTime.now(DATE_ZONE)
        if (now.minusHours(4).toDate().after(game.date)) return

        val title = ":popcorn: Partie ${if (game.isFinished()) "terminée" else "en cours"} sur KGS !"
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = game.description(),
            title = title
        )
    }
}
