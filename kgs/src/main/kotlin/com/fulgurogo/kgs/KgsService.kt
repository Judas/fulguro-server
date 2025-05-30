package com.fulgurogo.kgs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.Logger.Level.ERROR
import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
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

class KgsService : PeriodicFlowService(0, 5) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        log(INFO, "onTick")

        // Get stalest user
        KgsDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Scrap archives pages
                val games = scrapGames(stale)

                // Update user rank
                val updatedRank = games.maxByOrNull { it.date }?.let {
                    if (it.blackName == stale.kgsId) it.blackRank
                    else if (it.whiteName == stale.kgsId) it.whiteRank
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
                games.forEach {
                    if (!KgsDatabaseAccessor.existGame(it)) KgsDatabaseAccessor.addGame(it)
                }
            } catch (e: Exception) {
                log(ERROR, "onTick FAILURE ${e.message}")
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
        val html = Jsoup.connect(route).get()
        log(INFO, "Scraping $route")

        // Get the tables, there might be 0 (no games at all), 1 (no games this month) or 2 (games, yay !)
        val tables = html.select("table.grid").asList()
        if (tables.size == 2) extractGamesFrom(tables[0]) else mutableListOf()
    } catch (e: IOException) {
        log(INFO, "scrapMonthlyGames FAILURE ${e.message}")
        mutableListOf()
    }

    private fun extractGamesFrom(gameTable: Element): MutableList<KgsGame> {
        val gameRows = gameTable.select("tr").asList()
        gameRows.removeFirst() // First row is header
        return gameRows.mapNotNull { row ->
            val columns = row.select("td").asList()

            // Game result => skip unfinished games
            // W+score, W+RESIGN, W+FORFEIT, W+TIME
            // B+score, B+RESIGN, B+FORFEIT, B+TIME
            // UNKNOWN, UNFINISHED, NO_RESULT
            val result = columns[6].text().trim()
            if (!result.contains("+")) return@mapNotNull null

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
            val gobanSize = columns[3].text().trim()
            if (!sgf.contains("SZ[19]")) return@mapNotNull null
            if (!gobanSize.contains("19x19")) return@mapNotNull null

            // Get handicap from SGF
            val handicap = if (sgf.contains("]HA[")) getSgfProperty(sgf, "HA").toInt() else 0

            // Get komi from SGF
            val komi = if (sgf.contains("]KM[")) getSgfProperty(sgf, "KM").toFloat() else return@mapNotNull null

            // Get time setting from SGF
            val isLongGame = if (sgf.contains("]TM[")) getSgfProperty(sgf, "TM").toInt() > 1200 else false

            // Players
            val whitePlayer = columns[1].select("a").firstOrNull()?.text()?.trim().splitNameRank()
            val blackPlayer = columns[2].select("a").firstOrNull()?.text()?.trim().splitNameRank()

            // Date
            val dateString = columns[4].text().trim()
            val sdf = SimpleDateFormat("M/d/y h:mm a")
            sdf.timeZone = TimeZone.getTimeZone("GMT")
            val date = sdf.parse(dateString, ParsePosition(0))

            KgsGame(
                date = date,
                blackName = blackPlayer.first,
                blackRank = blackPlayer.second,
                blackWon = result.startsWith("B+"),
                whiteName = whitePlayer.first,
                whiteRank = whitePlayer.second,
                whiteWon = result.startsWith("W+"),
                komi = komi,
                handicap = handicap,
                longGame = isLongGame,
                sgf = sgf
            )
        }.toMutableList()
    }

    private fun fetchSgf(sgfLink: String, allowRetry: Boolean = true): String {
        val request: Request = Request.Builder().url(sgfLink).get().build()
        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            val apiResponse = response.body!!.string().replace("\n", "")
            response.close()
            apiResponse
        } else if (allowRetry) {
            // Retry once after delay
            Thread.sleep(1000L)
            log(INFO, "Fetching SGF ERROR: Waiting then retrying")
            fetchSgf(sgfLink, false)
        } else {
            // Failed twice
            log(ERROR, "Fetching SGF FAILURE " + response.code)
            ""
        }
    }

    private fun getSgfProperty(sgf: String, key: String): String {
        val keyIndex = sgf.indexOf("]$key[")
        val suffix = sgf.substring(keyIndex + 2 + key.length)
        return suffix.substring(0, suffix.indexOf("]"))
    }

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
}
