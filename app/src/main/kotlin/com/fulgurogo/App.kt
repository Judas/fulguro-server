package com.fulgurogo

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.db.ssh.SSHConnector
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.egf.EgfModule
import com.fulgurogo.ffg.FfgModule
import com.fulgurogo.fox.FoxModule
import com.fulgurogo.igs.IgsModule
import com.fulgurogo.kgs.KgsModule
import com.fulgurogo.ogs.OgsModule

const val TAG = "OldAppModule"

fun main() {
    val isDebug = Config.get("debug").toBoolean()

    // In dev we need to connect via SSH to the server for the MySQL access (only local connection allowed)
    if (isDebug) SSHConnector.connect()

    // Data aggregator modules
    DiscordModule.init()
    KgsModule.init()
    OgsModule.init()
    FoxModule.init()
    IgsModule.init()
    FfgModule.init()
    EgfModule.init()

    // TODO GoldTierModule (assign tier based on server ranks weights)
    // TODO FgcModule (level stability for FGC tournaments)
    // TODO CleanupModule (remove old users, users with failed linked accounts etc...)
    // TODO API module
    // TODO frontend ping module (every 14 minutes)

    // TODO Manage users on error somehow

    // TODO HouseModule
    // TODO CardsModule

//    // Launching server API
//    Javalin
//        .create { config ->
//            config.http.defaultContentType = "application/json"
//            if (isDebug) config.bundledPlugins.enableDevLogging()
//            config.bundledPlugins.enableCors { cors -> cors.addRule { it.anyHost() } }
//        }
//        .start(Config.get("ladder.api.port").toInt())
//        .apply {
//            get("/gold/api/players", Api::getPlayers)
//            get("/gold/api/player/{id}", Api::getPlayerProfile)
//            get("/gold/api/games", Api::getRecentGames)
//            get("/gold/api/game/{id}", Api::getGame)
//            get("/gold/api/fgc/validation", Api::getFgcValidation)
//            get("/gold/api/tiers", Api::getTiers)
//            post("/gold/api/auth", Api::authenticateUser)
//            get("/gold/api/auth/profile", Api::getAuthProfile)
//            get("/gold/api/scan", Api::isScanning)
//            get("/gold/api/accounts", Api::getAccounts)
//            post("/gold/api/link", Api::link)
//            delete("/gold/api/link", Api::unlink)
//            get("/gold/api/exam/ranking", Api::examRanking)
//            get("/gold/api/exam/titles", Api::examTitles)
//            get("/gold/api/exam/history", Api::examHistory)
//            get("/gold/api/exam/stats", Api::examStats)
//            // TODO Ad api to list all players in error for each service
//        }
}
