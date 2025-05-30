package com.fulgurogo

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.db.ssh.SSHConnector
import com.fulgurogo.kgs.KgsService

fun main() {
    // Launch Discord bot
//    val bot = DiscordBot() // TODO Extract this part into the discord Module
//    JDABuilder.createDefault(Config.get("bot.token"))
//        .setChunkingFilter(ChunkingFilter.ALL)
//        .setMemberCachePolicy(MemberCachePolicy.ALL)
//        .enableIntents(GatewayIntent.GUILD_MEMBERS)
//        .addEventListeners(FulguroBot)
//        .build()

    val isDebug = Config.get("debug").toBoolean()

    // In dev we need to connect via SSH to the server for the MySQL access (only local connection allowed)
    if (isDebug) SSHConnector.connect()

    // TODO DiscordService (pseudo avatar update)
    KgsService().start()
    // TODO OgsService
    // TODO FoxService
    // TODO IgsService
    // TODO FfgService
    // TODO EgfService
    // TODO service exam (needs the bot jda, pass it from here)
    // TODO service rank / tier (ladder)
    // TODO Service clean (old games / empty users)

//    // TODO Make API serviec
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
//        }
//
//    // Launching GameScanner
//    GameScanner.start()
}
