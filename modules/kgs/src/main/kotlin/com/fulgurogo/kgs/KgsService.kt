package com.fulgurogo.kgs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.common.utilities.scrap
import com.fulgurogo.common.utilities.sgfProperty
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.reconcileGames
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo
import okhttp3.Request
import okio.IOException
import org.jsoup.nodes.Element
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.*

class KgsService : StalestFirstService<KgsUserInfo>(0, 60, TAG) {
    private var lastNetworkCallTime: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

    override fun stalest(): KgsUserInfo? = KgsDatabaseAccessor.stalestUser()

    override fun markAsError(stale: KgsUserInfo) = KgsDatabaseAccessor.markAsError(stale)

    override fun refresh(stale: KgsUserInfo) {
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
                error = false
            )
        )

        // Add games in DB
        reconcileGames(games, KgsDatabaseAccessor, "KGS")
    }

    private fun scrapGames(stale: KgsUserInfo): List<KgsGame> = stale.kgsId?.let { kgsId ->
        val now = ZonedDateTime.now(DATE_ZONE)
        val lastMonth = now.minusMonths(1)

        val games = scrapMonthlyGames(stale.kgsId, now.year, now.monthValue)
        games.addAll(scrapMonthlyGames(stale.kgsId, lastMonth.year, lastMonth.monthValue))

        games
    } ?: throw Exception("Invalid KGS id")

    private fun scrapMonthlyGames(kgsId: String?, year: Int, month: Int): MutableList<KgsGame> = try {
        val route = "${Config.get("kgs.archives.url")}?user=$kgsId&year=$year&month=$month"
        val html = scrap(route)

        // Get the tables, there might be 0 (no games at all), 1 (no games this month) or 2 (games, yay !)
        val tables = html.select("table.grid").asList()
        if (tables.size == 2) extractGamesFrom(tables[0]) else mutableListOf()
    } catch (e: IOException) {
        log(TAG, "scrapMonthlyGames FAILURE ${e.message}")
        throw Exception(e)
    }

    private fun extractGamesFrom(gameTable: Element): MutableList<KgsGame> {
        val gameRows = gameTable.select("tr").asList()
        gameRows.removeFirst() // First row is header
        return gameRows.mapNotNull { row ->
            val columns = row.select("td").asList()
            if (columns.size != 7) return@mapNotNull null

            // Date => skip games older than 32 days
            val dateString = columns[4].text().trim()
            val sdf = SimpleDateFormat("M/d/y h:mm a")
            sdf.timeZone = TimeZone.getTimeZone("GMT")
            val date = sdf.parse(dateString, ParsePosition(0))
            val now = ZonedDateTime.now(DATE_ZONE)
            if (now.minusDays(32).toDate().after(date)) return@mapNotNull null

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
            val gameType = columns[5].text().trim().lowercase()
            if (listOf("challenge", "demonstration", "review", "rengo_review", "teaching", "rengo").contains(gameType))
                return@mapNotNull null
            val ranked = gameType == "ranked"

            // SGF Link => Skip private games
            val sgfLink = columns[0].select("a").firstOrNull()?.attr("href")
            if (sgfLink.isNullOrBlank()) return@mapNotNull null
            ensureSpamDelay()

            // Fetch SGF content from link
            val sgf = fetchSgf(sgfLink)
            if (sgf.isBlank()) return@mapNotNull null

            // Goban size => Skip wrong size games
            val size = sgf.sgfProperty("SZ")?.toIntOrNull() ?: 0

            // Get handicap from SGF
            val handicap = sgf.sgfProperty("HA")?.toIntOrNull() ?: 0

            // Get komi from SGF
            val komi = sgf.sgfProperty("KM")?.toFloatOrNull() ?: return@mapNotNull null

            // Get time setting from SGF
            val isLongGame = (sgf.sgfProperty("TM")?.toIntOrNull() ?: 0) > 1200

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
                ranked = ranked,
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
        val response = okHttpClient().newCall(request).execute()
        return if (response.isSuccessful) {
            val responseBody = response.body!!.string().replace("\n", "")
            response.close()
            responseBody
        } else if (allowRetry) {
            // Retry once after delay
            response.close()
            Thread.sleep(1000L)
            log(TAG, "Fetching SGF ERROR: Waiting then retrying")
            fetchSgf(sgfLink, false)
        } else {
            // Failed twice
            response.close()
            log(TAG, "Fetching SGF FAILURE " + response.code)
            ""
        }
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

    private fun ensureSpamDelay() {
        // Delay to avoid spamming OGS API: ensure between 500ms & 1500ms free time
        val now = ZonedDateTime.now(DATE_ZONE)
        if (lastNetworkCallTime.plusSeconds(1).isAfter(now))
            Thread.sleep(500)
        lastNetworkCallTime = ZonedDateTime.now(DATE_ZONE)
    }

}
