package com.fulgurogo.api

import com.fulgurogo.api.ApiModule.TAG
import com.fulgurogo.api.db.ApiDatabaseAccessor
import com.fulgurogo.api.utilities.internalError
import com.fulgurogo.api.utilities.notFoundError
import com.fulgurogo.api.utilities.rateLimit
import com.fulgurogo.api.utilities.standardResponse
import com.fulgurogo.common.logger.log
import io.javalin.http.Context

class Api {
    fun getPlayers(context: Context) = try {
        context.rateLimit()
        val players = ApiDatabaseAccessor.apiPlayers()
        context.standardResponse(players)
    } catch (e: Exception) {
        log(TAG, "getPlayers ${e.message}")
        context.internalError()
    }

    fun getPlayerProfile(context: Context) = try {
        context.rateLimit()

        val playerId = context.pathParam("id")
        val player = ApiDatabaseAccessor.apiPlayer(playerId)

        player?.let { p ->
            // Accounts
            p.accounts = ApiDatabaseAccessor.apiAccountsFor(playerId)

//            // Games
//            p.games = ApiDatabaseAccessor.apiGamesFor(p.discordId)
//                .map { ApiGame.from(it, p.discordId == it.blackPlayerDiscordId) }
//                .toMutableList()
            context.standardResponse(p)
        } ?: context.notFoundError()
    } catch (e: Exception) {
        log(TAG, "getPlayerProfile", e)
        context.internalError()
    }
}
