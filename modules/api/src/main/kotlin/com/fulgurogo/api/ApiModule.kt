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

                // Houses: plural for the list, singular for one house, as with players and games
                get("/gold/api/houses", api::getHouses)
                get("/gold/api/house/{slug}", api::getHouse)
                // Mutations. No collision with the GET above: Javalin matches on the method too
                post("/gold/api/house/join", api::joinHouse)
                post("/gold/api/house/choice", api::setHouseChoice)

                // League: plural-free, the path is /league on both sides -- API and website page
                get("/gold/api/league", api::getLeague)
                get("/gold/api/league/session/{number}", api::getLeagueSession)
                // Mutations. No collision with the GETs above: Javalin matches on the method too
                post("/gold/api/league/join", api::joinLeague)
                post("/gold/api/league/leave", api::leaveLeague)

                // Service liveness: 200 when every background service is healthy, 503 otherwise
                get("/gold/api/health", api::getHealth)

                // Auth
                post("/gold/api/auth", api::authenticateUser)
                get("/gold/api/auth/profile", api::getAuthProfile)

                // Read-only administration
                get("/gold/api/admin/logs", api::getAdminLogs)

                // Accounts
                get("/gold/api/accounts", api::getAccounts)
                post("/gold/api/link", api::link)
            }
    }
}
