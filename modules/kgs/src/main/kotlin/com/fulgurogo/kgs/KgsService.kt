package com.fulgurogo.kgs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.common.utilities.sgfProperty
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.reconcileGames
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo
import kotlinx.coroutines.delay
import okhttp3.Request
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

private const val UNKNOWN_RANK = "?"

/** How many archive pages beyond the two a refresh already fetches may be read looking for a settled rank. */
private const val MAX_EXTRA_RANK_PAGES = 6
private val MONTH_LINK_YEAR = Regex("[?&]year=(\\d+)")
private val MONTH_LINK_MONTH = Regex("[?&]month=(\\d+)")

class KgsService : StalestFirstService<KgsUserInfo>(0, 60, TAG) {
    private var lastNetworkCallTime: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

    override fun stalest(): KgsUserInfo? = KgsDatabaseAccessor.stalestUser()

    override fun markAsError(stale: KgsUserInfo) = KgsDatabaseAccessor.markAsError(stale)

    override suspend fun refresh(stale: KgsUserInfo) {
        // One row is stored with a trailing space, and the archive cells have to be matched case insensitively anyway
        val kgsId = stale.kgsId?.trim()
        if (kgsId.isNullOrBlank()) throw Exception("Invalid KGS id")

        val now = ZonedDateTime.now(DATE_ZONE)
        val lastMonth = now.minusMonths(1)

        // Scrap archives pages. Any month page also carries the chart of every month the player has games in, so
        // these two answer both the game import and the rank lookup.
        val pages = mapOf(
            (now.year to now.monthValue) to scrapArchives(kgsId, now.year, now.monthValue),
            (lastMonth.year to lastMonth.monthValue) to scrapArchives(kgsId, lastMonth.year, lastMonth.monthValue)
        )
        val games = pages.values.flatMap { page -> gamesTableOf(page)?.let { extractGamesFrom(it) } ?: listOf() }

        // Update user rank
        val rank = scrapRank(kgsId, pages)
        KgsDatabaseAccessor.updateUser(
            KgsUserInfo(
                discordId = stale.discordId,
                kgsId = stale.kgsId,
                kgsRank = rank?.first ?: UNKNOWN_RANK,
                kgsRankDate = rank?.second,
                updated = Date(),
                error = false
            )
        )

        // Add games in DB
        reconcileGames(games, KgsDatabaseAccessor, "KGS")
    }

    /**
     * The player's most recent settled rank, with the date of the game it was read from.
     *
     * Deliberately not taken from the games imported above, which are only the ones inside the 32-day window. KGS
     * publishes no current rank anywhere -- `graphPage.jsp` serves a PNG and nothing else -- only the rank a player
     * held in each archived game. So a rank of any age will do and `UserRanks.computeRating` fades the weight by its
     * age; what will *not* do is a provisional "2d?", which is what KGS shows an account that drifted while idle, so
     * those rows are walked past rather than read as a rank (see [isSettledRank]).
     *
     * Walking back is bounded to [MAX_EXTRA_RANK_PAGES] extra pages: a chart can span years, and a player whose last
     * settled rank sits further back than that keeps no rank until they play again. It costs nothing for anyone who
     * played this month or last, whose pages are already in hand.
     */
    private suspend fun scrapRank(kgsId: String, scrapped: Map<Pair<Int, Int>, Document>): Pair<String, Date>? {
        // The two months already in hand are the newest there are, so read them before fetching anything
        scrapped.values
            .mapNotNull { page -> gamesTableOf(page)?.let { rankFrom(it, kgsId) } }
            .maxByOrNull { it.second }
            ?.let { return it }

        val months = scrapped.values
            .map { monthsWithGames(it) }
            .firstOrNull { it.isNotEmpty() }
            ?.filter { it !in scrapped.keys }
            ?: return null

        // Newest first, and stop at the first month that yields a settled rank
        return months
            .reversed()
            .take(MAX_EXTRA_RANK_PAGES)
            .firstNotNullOfOrNull { (year, month) ->
                ensureSpamDelay()
                gamesTableOf(scrapArchives(kgsId, year, month))?.let { rankFrom(it, kgsId) }
            }
    }

    /** Archive pages are behind a KGS login — [KgsSession] holds the session and renews it on its own. */
    private suspend fun scrapArchives(kgsId: String, year: Int, month: Int): Document = try {
        KgsSession.scrap("${Config.get("kgs.archives.url")}?user=$kgsId&year=$year&month=$month") { ensureSpamDelay() }
    } catch (e: IOException) {
        log(TAG, "scrapArchives FAILURE ${e.message}")
        throw Exception(e)
    }

    /**
     * The games table of an archive page, or null when the player has no game that month.
     * There might be 0 tables (no games at all), 1 (the month chart alone) or 2 (games, yay !).
     */
    private fun gamesTableOf(html: Document): Element? = html
        .select("table.grid").asList()
        .takeIf { it.size == 2 }
        ?.first()

    /**
     * Every (year, month) the month chart links to, oldest first, empty when the player never played.
     *
     * The chart runs oldest year first and January to December within a year, hence the ordering. It does not link the
     * month currently displayed, so the page's own month is never in here -- which is exactly why [scrapRank] reads the
     * pages it already has before trusting this.
     */
    private fun monthsWithGames(html: Document): List<Pair<Int, Int>> = html
        .select("table.grid").asList()
        .lastOrNull()
        ?.select("a[href*=month]")
        ?.mapNotNull { link ->
            val href = link.attr("href")
            val year = MONTH_LINK_YEAR.find(href)?.groupValues?.get(1)?.toIntOrNull()
            val month = MONTH_LINK_MONTH.find(href)?.groupValues?.get(1)?.toIntOrNull()
            if (year != null && month != null) year to month else null
        }
        ?: listOf()

    /** The most recent settled rank of [kgsId] in this games table, with the date of the game it comes from. */
    private fun rankFrom(gameTable: Element, kgsId: String): Pair<String, Date>? {
        val dateFormat = archiveDateFormat()

        return gameTable
            .select("tr").asList()
            .drop(1) // First row is header
            .mapNotNull { row ->
                val columns = row.select("td").asList()
                if (columns.size != 7) return@mapNotNull null

                val date = dateFormat.parse(columns[4].text().trim(), ParsePosition(0)) ?: return@mapNotNull null
                val rank = listOf(columns[1], columns[2]) // White then Black
                    .map { it.select("a").firstOrNull()?.text()?.trim().splitNameRank() }
                    .firstOrNull { it.first.equals(kgsId, ignoreCase = true) }
                    ?.second
                    ?: return@mapNotNull null

                if (rank.isSettledRank()) rank to date else null
            }
            .maxByOrNull { it.second }
    }

    private suspend fun extractGamesFrom(gameTable: Element): MutableList<KgsGame> {
        val gameRows = gameTable.select("tr").asList()
        gameRows.removeFirst() // First row is header

        val dateFormat = archiveDateFormat()

        return gameRows.mapNotNull { row ->
            val columns = row.select("td").asList()
            if (columns.size != 7) return@mapNotNull null

            // Date => skip rows we cannot date, then games older than 32 days.
            // parse(String, ParsePosition) reports failure by returning null rather than throwing, and that null used
            // to travel on as a platform-typed Date until it blew up on date.time below.
            val dateString = columns[4].text().trim()
            val date = dateFormat.parse(dateString, ParsePosition(0)) ?: return@mapNotNull null
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

    /**
     * The archive pages are English because `scrap()` asks for English -- do not change that header without reading
     * the note on it, a French page dates games "28/07/26 05:39" and calls white wins "B+", for *Blanc*.
     *
     * The locale is pinned here for the same reason rather than inherited from the JVM: the AM/PM markers are
     * "AM"/"PM" under most locales but not all (ja_JP gives 午前/午後, zh_CN 上午/下午), and there the parse would
     * fail for every row. Built once per table instead of once per row, and never held in a field, because
     * SimpleDateFormat is not thread safe.
     */
    private fun archiveDateFormat(): SimpleDateFormat = SimpleDateFormat("M/d/y h:mm a", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }

    private suspend fun fetchSgf(sgfLink: String, allowRetry: Boolean = true): String {
        val request = Request.Builder()
            .url(sgfLink)
            .header("User-Agent", Config.get("user.agent"))
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) return response.body.string().replace("\n", "")

            if (!allowRetry) {
                // Failed twice
                log(TAG, "Fetching SGF FAILURE " + response.code)
                return ""
            }
        }

        // Retry once after delay
        delay(1000.milliseconds)
        log(TAG, "Fetching SGF ERROR: Waiting then retrying")
        return fetchSgf(sgfLink, false)
    }

    /**
     * Splits an archive player cell ("Modoki [1d]") into its name and its rank, as KGS shows it: "1d", "2d?" for a
     * rank KGS is not confident in, "?" when there is none ("[?]" and "[-]" both mean that).
     *
     * The provisional marker is kept rather than stripped. It is not decoration: an account that stops playing drifts
     * and comes back marked "2d?" while really being nothing of the sort, so a "2d?" must never be read as "2d" --
     * see [isSettledRank], which is what the stored rank goes through.
     */
    private fun String?.splitNameRank(): Pair<String, String> {
        if (isNullOrBlank()) return "" to UNKNOWN_RANK

        val name = substringBefore(" ").trim()
        val rank = substringAfter(" ", "").trim().removeSurrounding("[", "]")

        return name to if (rank.any { it.isDigit() }) rank else UNKNOWN_RANK
    }

    /** Whether this is a rank KGS stands behind: "5k" and "2d" are, "2d?" and "?" are not. */
    private fun String.isSettledRank(): Boolean = !endsWith("?") && any { it.isDigit() }

    private suspend fun ensureSpamDelay() {
        // Delay to avoid spamming KGS API: ensure between 500ms & 1500ms free time
        val now = ZonedDateTime.now(DATE_ZONE)
        if (lastNetworkCallTime.plusSeconds(1).isAfter(now))
            delay(500.milliseconds)
        lastNetworkCallTime = ZonedDateTime.now(DATE_ZONE)
    }
}
