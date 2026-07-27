package com.fulgurogo.ogs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.rankToKyuDanString
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.reconcileGames
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

class OgsService : StalestFirstService<OgsUserInfo>(0, 15, TAG) {
    private val ogsApiClient = OgsApiClient()

    override fun stalest(): OgsUserInfo? = OgsDatabaseAccessor.stalestUser()

    override fun markAsError(stale: OgsUserInfo) = OgsDatabaseAccessor.markAsError(stale)

    override suspend fun refresh(stale: OgsUserInfo) {
        // Get user profile
        val rating = fetchPlayerRating(stale)
        if (rating == null) {
            markAsError(stale)
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

        // Add games in DB. Only the caller that actually wrote the row notifies; the real time service races us here.
        reconcileGames(fetchPlayerGames(stale), OgsDatabaseAccessor, "OGS")
    }

    private fun fetchPlayerRating(stale: OgsUserInfo): OgsApiPlayerRating? {
        val route = "${Config.get("ogs.termination.api.url")}/player/${stale.ogsId}"
        return ogsApiClient.get(route, OgsApiPlayerRating::class.java)
    }

    private fun fetchPlayerGames(stale: OgsUserInfo): List<OgsGame> {
        // OGS puts ongoing correspondence games at the top of the list
        // So we parse results until we get a page with live games
        var pageIndex = 0
        var games: MutableList<OgsApiGame>
        do {
            pageIndex++
            val route = "${Config.get("ogs.api.url")}/players/${stale.ogsId}/games?ordering=-ended&page=$pageIndex"
            games = ogsApiClient.get(route, OgsApiGameList::class.java).results.toMutableList()
        } while (games.all { it.isCorrespondence() })

        // Then we get the following page, this ensures having AT LEAST 1 full page of live games
        pageIndex++
        val route = "${Config.get("ogs.api.url")}/players/${stale.ogsId}/games?ordering=-ended&page=$pageIndex"
        games.addAll(ogsApiClient.get(route, OgsApiGameList::class.java).results)

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

            // Date => skip games we cannot date, then games older than 32 days
            val date = it.date() ?: return@mapNotNull null
            val now = ZonedDateTime.now(DATE_ZONE)
            if (now.minusDays(32).toDate().after(date)) return@mapNotNull null

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
        ogsApiClient.get("${Config.get("ogs.api.url")}/games/${game.id}/sgf")
    } catch (_: Exception) {
        ""
    }

}
