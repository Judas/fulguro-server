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

        val apiGames = fetchPlayerApiGames(stale)

        // Undo what OGS has voided, before ingesting the rest. This is the only path that can: the WebSocket writes games
        // from the live list and knows nothing of annulment, and an annulment almost always lands after the game ended.
        OgsDatabaseAccessor.removeAnnulledGames(apiGames.filter { it.annulled }.map { it.goldId() })

        // Add games in DB. Only the caller that actually wrote the row notifies; the real time service races us here.
        reconcileGames(apiGames.mapNotNull { toGame(it) }, OgsDatabaseAccessor, "OGS")
    }

    private fun fetchPlayerRating(stale: OgsUserInfo): OgsApiPlayerRating? {
        val route = "${Config.get("ogs.termination.api.url")}/player/${stale.ogsId}"
        return ogsApiClient.get(route, OgsApiPlayerRating::class.java)
    }

    /**
     * The player's recent games as OGS returns them, unfiltered.
     *
     * Split from [toGame] so that `refresh` sees the annulled ones too: they used to be dropped here, which meant the one
     * place in the application that knows a game has been voided threw that knowledge away.
     */
    private fun fetchPlayerApiGames(stale: OgsUserInfo): List<OgsApiGame> {
        // OGS puts ongoing correspondence games at the top of the list
        // So we parse results until we get a page with live games.
        // Paging follows the `next` URL the API hands us rather than a page counter we build ourselves: a player with
        // few games has no page 2, and asking for one is a 404.
        val firstRoute = "${Config.get("ogs.api.url")}/players/${stale.ogsId}/games?ordering=-ended"
        var page = ogsApiClient.get(firstRoute, OgsApiGameList::class.java)
        while (page.nextRoute() != null && page.results.all { it.isCorrespondence() })
            page = ogsApiClient.get(page.nextRoute()!!, OgsApiGameList::class.java)

        // Then we get the following page, this ensures having AT LEAST 1 full page of live games
        val games = page.results.toMutableList()
        page.nextRoute()?.let { games.addAll(ogsApiClient.get(it, OgsApiGameList::class.java).results) }

        return games
    }

    /** One API game as a row, or null when it is not a game the ladder counts. */
    private fun toGame(game: OgsApiGame): OgsGame? {
        // Skip cancelled games. They are not merely ignored any more: `refresh` deletes the stored ones first.
        if (game.annulled) return null

        // Skip non-square goban
        if (game.height != game.width) return null

        // Skip bot games
        if (game.players.black.isBot() || game.players.white.isBot()) return null

        // Skip correspondence games
        if (game.isCorrespondence()) return null

        // Skip rengo
        if (game.rengo) return null

        // Skip weird result
        val result = game.result() ?: return null

        // Date => skip games we cannot date, then games older than 32 days
        val date = game.date() ?: return null
        val now = ZonedDateTime.now(DATE_ZONE)
        if (now.minusDays(32).toDate().after(date)) return null

        // Fetch SGF
        val sgf = fetchSgf(game)

        return OgsGame(
            goldId = game.goldId(),
            id = game.id,
            date = date,
            blackId = game.players.black.id,
            blackName = game.players.black.username,
            blackRank = game.players.black.ranking.rankToKyuDanString(),
            whiteId = game.players.white.id,
            whiteName = game.players.white.username,
            whiteRank = game.players.white.ranking.rankToKyuDanString(),
            size = game.width,
            komi = game.komi.toDouble(),
            handicap = game.handicap,
            ranked = game.ranked,
            longGame = game.isLongGame(),
            result = result,
            sgf = sgf
        )
    }

    private fun fetchSgf(game: OgsApiGame): String = try {
        ogsApiClient.get("${Config.get("ogs.api.url")}/games/${game.id}/sgf")
    } catch (_: Exception) {
        ""
    }

}
