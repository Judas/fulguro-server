package com.fulgurogo.api

import com.fulgurogo.common.config.Config
import io.javalin.Javalin

/**
 * This module is in charge of exposing an API for the website.
 */
object ApiModule {
    const val TAG = "API"

    private val api = Api()

    fun init(isDebug: Boolean) {
        // Launching server API
        Javalin
            .create { config ->
                config.http.defaultContentType = "application/json"
                if (isDebug) config.bundledPlugins.enableDevLogging()
                config.bundledPlugins.enableCors { cors -> cors.addRule { it.anyHost() } }
            }
            .start(Config.get("gold.api.port").toInt())
            .apply {
                get("/gold/api/players", api::getPlayers)
                get("/gold/api/player/{id}", api::getPlayerProfile)
//                get("/gold/api/games", goldApi::getRecentGames)
//                get("/gold/api/game/{id}", goldApi::getGame)
//
//                get("/gold/api/fgc/validation", goldApi::getFgcValidation)
//                get("/gold/api/tiers", goldApi::getTiers)
//                post("/gold/api/auth", goldApi::authenticateUser)
//                get("/gold/api/auth/profile", goldApi::getAuthProfile)
//                get("/gold/api/scan", goldApi::isScanning)
//                get("/gold/api/accounts", goldApi::getAccounts)
//                post("/gold/api/link", goldApi::link)
//                delete("/gold/api/link", goldApi::unlink)
//                get("/gold/api/exam/ranking", goldApi::examRanking)
//                get("/gold/api/exam/titles", goldApi::examTitles)
//                get("/gold/api/exam/history", goldApi::examHistory)
//                get("/gold/api/exam/stats", goldApi::examStats)
                // TODO Ad api to list all players in error for each service ?
            }
    }
}
