package com.fulgurogo.discord

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.discord.DiscordModule.TAG
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.discord.db.model.DiscordUserInfo
import java.util.*

class DiscordService(private val discordBot: DiscordBot) : PeriodicFlowService(0, 5) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        DiscordDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // In debug, users are not in the server, just keep their name or the cleaner service will remove them
                // In prod, get the updated name from Discord
                val discordName =
                    if (Config.get("debug").toBoolean()) stale.discordName
                    else discordBot.jda?.let { jda ->
                        val discordUser = jda.getUserById(stale.discordId)
                        val guild = jda.getGuildById(Config.get("bot.guild.id"))
                        discordUser?.let {
                            guild?.getMember(it)?.effectiveName ?: it.name
                        } ?: stale.discordId
                    } ?: stale.discordId

                // Update avatar
                val discordAvatar = discordBot.jda?.getUserById(stale.discordId)?.effectiveAvatarUrl
                    ?: Config.get("gold.default.avatar")

                DiscordDatabaseAccessor.updateUser(
                    DiscordUserInfo(
                        discordId = stale.discordId,
                        discordName = discordName,
                        discordAvatar = discordAvatar,
                        updated = Date(),
                        error = false
                    )
                )
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                DiscordDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
