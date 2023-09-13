package com.fulgurogo

import com.fulgurogo.features.api.Api
import com.fulgurogo.features.bot.FulguroBot
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.features.ssh.SSHConnector
import io.javalin.Javalin
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy

fun main() {
    // In dev we need to connect via SSH to the server for the MySQL access (only local connection allowed)
    if (Config.DEV) SSHConnector.connect()

    JDABuilder.createDefault(Config.Bot.TOKEN)
        .setChunkingFilter(ChunkingFilter.ALL)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .enableIntents(GatewayIntent.GUILD_MEMBERS)
        .addEventListeners(FulguroBot)
        .build()

    // Launching server API
    Javalin
        .create { config ->
            config.http.defaultContentType = "application/json"
            if (Config.DEV) config.plugins.enableDevLogging()
            config.plugins.enableCors { cors -> cors.add { it.anyHost() } }
        }
        .start(Config.Server.PORT)
        .apply {
            get("/gold/api/players", Api::getPlayers)
            get("/gold/api/player/{id}", Api::getPlayerProfile)
            get("/gold/api/games", Api::getRecentGames)
            get("/gold/api/game/{id}", Api::getGame)
            get("/gold/api/stability", Api::getStabilityOptions)
            get("/gold/api/tiers", Api::getTiers)
            post("/gold/api/auth", Api::authenticateUser)
            get("/gold/api/auth/profile", Api::getAuthProfile)
            get("/gold/api/scan", Api::isScanning)
            post("/gold/api/link", Api::link)
            delete("/gold/api/link", Api::unlink)
            get("/gold/api/exam/ranking", Api::examRanking)
            get("/gold/api/exam/history", Api::examHistory)
        }

    // Launching GameScanner
    GameScanner.start()
}
