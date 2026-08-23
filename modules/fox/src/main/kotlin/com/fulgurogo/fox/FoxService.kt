package com.fulgurogo.fox

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.sgfProperty
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.fox.FoxModule.TAG
import com.fulgurogo.fox.api.FoxApiClient
import com.fulgurogo.fox.api.FoxApiGame
import com.fulgurogo.fox.api.FoxApiPlayer
import com.fulgurogo.fox.db.FoxDatabaseAccessor
import com.fulgurogo.fox.db.model.FoxGame
import com.fulgurogo.fox.db.model.FoxUserInfo
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

private const val MAX_PAGES = 50
private const val RECENT_DAYS = 32L

class FoxService(
    private val apiClient: FoxApiClient = FoxApiClient(),
) : StalestFirstService<FoxUserInfo>(0, 300, TAG) {
    override fun stalest(): FoxUserInfo? = FoxDatabaseAccessor.stalestUser()

    override fun markAsError(stale: FoxUserInfo) = FoxDatabaseAccessor.markAsError(stale)

    override suspend fun refresh(stale: FoxUserInfo) {
        val player = apiClient.findPlayer(stale.foxName)
            ?: throw IllegalStateException("FOX player ${stale.foxId} was not found")

        if (!countersChanged(stale, player)) {
            FoxDatabaseAccessor.markScanned(stale, player)
            return
        }

        val games = recentGames(stale.foxId)
        val ids = games.mapNotNull { it.chessid }
        val stored = FoxDatabaseAccessor.storedChessIds(ids)
        var inserted = 0
        games.filter { it.chessid !in stored }.forEach { apiGame ->
            val chessId = apiGame.chessid ?: return@forEach
            val sgf = apiClient.game(chessId).sgf
            val game = sgf?.let { toGame(apiGame, it) }
            if (game == null) {
                log(TAG, "Skipping malformed FOX game $chessId")
            } else if (FoxDatabaseAccessor.addGame(game)) {
                inserted++
            }
        }

        FoxDatabaseAccessor.markScanned(stale, player)
        log(TAG, "Scanned ${stale.foxId}: ${games.size} recent game(s), $inserted inserted")
    }

    private fun countersChanged(user: FoxUserInfo, player: FoxApiPlayer): Boolean =
        user.totalWin != player.totalwin || user.totalLost != player.totallost || user.totalEqual != player.totalequal

    private fun recentGames(uid: String): List<FoxApiGame> {
        val cutoff = ZonedDateTime.now(DATE_ZONE).minusDays(RECENT_DAYS).toDate()
        val games = mutableListOf<FoxApiGame>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null

        repeat(MAX_PAGES) {
            val page = apiClient.games(uid, cursor)
            if (page.games.isEmpty()) return games

            val dated = page.games.mapNotNull { game -> game.date()?.let { game to it } }
            games += dated.filter { (_, date) -> !date.before(cutoff) }.map { it.first }

            if (dated.isNotEmpty() && dated.all { (_, date) -> date.before(cutoff) }) return games
            val next = page.lastCode?.takeIf { it.isNotBlank() }
                ?: page.games.lastOrNull()?.chessid?.takeIf { !it.isNullOrBlank() }
                ?: return games
            if (next == cursor || !seenCursors.add(next)) return games
            cursor = next
        }

        throw IllegalStateException("FOX game pagination exceeded $MAX_PAGES pages")
    }

    private fun toGame(apiGame: FoxApiGame, sgf: String): FoxGame? {
        val chessId = apiGame.chessid ?: return null
        val date = apiGame.date() ?: return null
        val blackId = apiGame.blackuid ?: return null
        val whiteId = apiGame.whiteuid ?: return null
        val size = sgf.sgfProperty("SZ")?.toIntOrNull() ?: return null
        val komi = sgf.sgfProperty("KM")?.toDoubleOrNull()?.div(50) ?: return null
        val handicap = sgf.sgfProperty("HA")?.toIntOrNull() ?: 0
        val result = when {
            sgf.sgfProperty("RE")?.startsWith("B+", ignoreCase = true) == true -> "black"
            sgf.sgfProperty("RE")?.startsWith("W+", ignoreCase = true) == true -> "white"
            sgf.sgfProperty("RE")?.equals("jigo", ignoreCase = true) == true -> "jigo"
            sgf.sgfProperty("RE") == "0" -> "jigo"
            else -> return null
        }

        return FoxGame(
            goldId = "FOX_$chessId",
            chessId = chessId,
            date = date,
            blackId = blackId,
            blackName = apiGame.blacknick ?: "?",
            blackRank = foxRank(apiGame.blackdan),
            whiteId = whiteId,
            whiteName = apiGame.whitenick ?: "?",
            whiteRank = foxRank(apiGame.whitedan),
            size = size,
            komi = komi,
            handicap = handicap,
            result = result,
            sgf = sgf,
        )
    }

    private fun FoxApiGame.date(): Date? = try {
        starttime?.let {
            Date.from(LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(DATE_ZONE).toInstant())
        }
    } catch (_: Exception) {
        null
    }

    private fun foxRank(dan: Int?): String = when {
        dan == null || dan < 0 -> "?"
        dan <= 17 -> "${18 - dan}k"
        else -> "${dan - 17}d"
    }
}
