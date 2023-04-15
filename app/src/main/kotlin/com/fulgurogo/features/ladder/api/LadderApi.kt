package com.fulgurogo.features.ladder.api

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.utilities.DATE_ZONE
import com.fulgurogo.utilities.rateLimit
import com.fulgurogo.utilities.standardResponse
import com.fulgurogo.utilities.toDate
import io.javalin.http.Context
import java.time.ZonedDateTime

object LadderApi {
    fun getPlayers(context: Context) {
        context.rateLimit()
        val players = DatabaseAccessor.apiLadderPlayers()
        context.standardResponse(players)
    }

    fun getPlayerProfile(context: Context) {
        context.rateLimit()

        val playerId = context.pathParam("id")
        val player = DatabaseAccessor.apiLadderPlayer(playerId)

        player?.let { p ->
            // Stability
            p.stability = DatabaseAccessor.stability(p.discordId)

            // Games
            p.games = DatabaseAccessor.apiLadderGamesFor(p.discordId)
                .map { ApiGame.from(it, p.discordId == it.blackPlayerDiscordId) }
                .toMutableList()

            context.standardResponse(p)
        } ?: context.status(404)
    }

    fun getRecentGames(context: Context) {
        context.rateLimit()
        val latestGames = DatabaseAccessor
            .apiLadderRecentGames()
            .map { ApiGame.from(it) }
        context.standardResponse(latestGames)
    }

    fun getGame(context: Context) {
        context.rateLimit()
        val gameId = context.pathParam("id")

        val game = DatabaseAccessor
            .apiLadderGame(gameId)
            ?.let { ApiGame.from(it) }

        game?.let { context.standardResponse(it) } ?: context.status(404)
    }

    fun getStabilityOptions(context: Context) {
        context.rateLimit()
        DatabaseAccessor.stability()
            ?.let { context.standardResponse(it) }
            ?: context.status(404)
    }
}
