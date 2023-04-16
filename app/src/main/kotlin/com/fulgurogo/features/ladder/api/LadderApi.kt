package com.fulgurogo.features.ladder.api

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.utilities.internalError
import com.fulgurogo.utilities.notFoundError
import com.fulgurogo.utilities.rateLimit
import com.fulgurogo.utilities.standardResponse
import io.javalin.http.Context

object LadderApi {
    fun getPlayers(context: Context) = try {
        context.rateLimit()
        val players = DatabaseAccessor.apiLadderPlayers()
        context.standardResponse(players)
    } catch (e: Exception) {
        context.internalError()
    }

    fun getPlayerProfile(context: Context) = try {
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
        } ?: context.notFoundError()
    } catch (e: Exception) {
        context.internalError()
    }

    fun getRecentGames(context: Context) = try {
        context.rateLimit()
        val latestGames = DatabaseAccessor
            .apiLadderRecentGames()
            .map { ApiGame.from(it) }

        context.standardResponse(latestGames)
    } catch (e: Exception) {
        context.internalError()
    }

    fun getGame(context: Context) = try {
        context.rateLimit()
        val gameId = context.pathParam("id")

        val game = DatabaseAccessor
            .apiLadderGame(gameId)
            ?.let { ApiGame.from(it) }

        game?.let { context.standardResponse(it) } ?: context.notFoundError()
    } catch (e: Exception) {
        context.internalError()
    }

    fun getStabilityOptions(context: Context) = try {
        context.rateLimit()
        DatabaseAccessor.stability()
            ?.let { context.standardResponse(it) }
            ?: context.notFoundError()
    } catch (e: Exception) {
        context.internalError()
    }
}
