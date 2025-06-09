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
                // Players
                get("/gold/api/players", api::getPlayers)
                get("/gold/api/player/{id}", api::getPlayerProfile)

                // Games
                get("/gold/api/games", api::getRecentGames)
                get("/gold/api/game/{id}", api::getGame)

                // Gold tiers
                get("/gold/api/tiers", api::getTiers)

                // Auth
                post("/gold/api/auth", api::authenticateUser)
                get("/gold/api/auth/profile", api::getAuthProfile)

                // Accounts
                get("/gold/api/accounts", api::getAccounts)
                post("/gold/api/link", api::link)
            }
    }
}
