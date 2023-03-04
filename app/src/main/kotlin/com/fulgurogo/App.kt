package com.fulgurogo

import com.fulgurogo.features.bot.FulguroBot
import com.fulgurogo.features.ladder.api.FulguroApi
import com.fulgurogo.features.ssh.SSHConnector
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
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

            config.staticFiles.add { staticFileConfig ->
                staticFileConfig.hostedPath = Config.Ladder.STATIC_PATH
                staticFileConfig.directory = Config.Ladder.STATIC_FOLDER
                staticFileConfig.location = Location.EXTERNAL
            }

            if (Config.DEV) config.plugins.enableDevLogging()
            config.plugins.enableCors { cors -> cors.add { it.anyHost() } }
        }
        .start(Config.Server.PORT)
        .apply {
            get("/gold/api/v5/players", FulguroApi::getPlayers)
        }
}
