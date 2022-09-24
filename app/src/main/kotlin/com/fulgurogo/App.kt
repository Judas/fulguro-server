package com.fulgurogo

import com.fulgurogo.features.bot.FulguroBot
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
            config.defaultContentType = "application/json"
            config.enableCorsForAllOrigins()
            if (Config.DEV) config.enableDevLogging()
            config.addStaticFiles { staticFileConfig ->
                staticFileConfig.hostedPath = Config.Ladder.STATIC_PATH
                staticFileConfig.directory = Config.Ladder.STATIC_FOLDER
                staticFileConfig.location = Location.EXTERNAL
            }
        }
        .start(Config.Server.PORT)
}
