package com.fulgurogo.discord

import com.fulgurogo.common.config.Config
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy

object DiscordModule {
    const val TAG = "DISCORD"

    val discordBot = DiscordBot()
    private val discordService = DiscordService(discordBot)

    fun init() {
        // Bot
        JDABuilder.createDefault(Config.get("bot.token"))
            .setChunkingFilter(ChunkingFilter.ALL)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .enableIntents(GatewayIntent.GUILD_MEMBERS)
            .addEventListeners(discordBot)
            .build()

        // User info service
        discordService.start()
    }
}
