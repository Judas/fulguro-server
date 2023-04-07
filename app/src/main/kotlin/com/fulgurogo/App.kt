package com.fulgurogo

import com.fulgurogo.features.bot.FulguroBot
import com.fulgurogo.features.ladder.api.LadderApi
import com.fulgurogo.features.ssh.SSHConnector
import io.javalin.Javalin
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy

fun main() {
    // In dev we need to connect via SSH to the server for the MySQL access
    if (Config.DEV) SSHConnector.connect()

    // Launching bot
    JDABuilder.createDefault(Config.Bot.TOKEN)
        .setChunkingFilter(ChunkingFilter.ALL)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .enableIntents(GatewayIntent.GUILD_MEMBERS)
        .addEventListeners(FulguroBot())
        .build()

    // Launching API server
    Javalin
        .create { config ->
            config.http.defaultContentType = "application/json"
            if (Config.DEV) config.plugins.enableDevLogging()
            config.plugins.enableCors { cors -> cors.add { it.anyHost() } }
        }
        .start(Config.Server.PORT)
        .apply {
            get("/gold/api/players", LadderApi::getPlayers)
            get("/gold/api/player/{id}", LadderApi::getPlayerProfile)

            get("/gold/api/games", LadderApi::getRecentGames)
            get("/gold/api/game/{id}", LadderApi::getGame)

            get("/gold/api/stability", LadderApi::getStabilityOptions)
        }
}
