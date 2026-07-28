package com.fulgurogo.discord

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.discord.DiscordModule.TAG
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.discord.db.model.DiscordUserInfo
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import java.util.*

class DiscordService(private val discordBot: DiscordBot) : StalestFirstService<DiscordUserInfo>(0, 5, TAG) {
    override fun stalest(): DiscordUserInfo? = DiscordDatabaseAccessor.stalestUser()

    override fun markAsError(stale: DiscordUserInfo) = DiscordDatabaseAccessor.markAsError(stale)

    /**
     * Reads the profile from Discord, and records a departure only when Discord actually says so.
     *
     * The distinction matters because the clean module deletes a departed user from every table, which cannot be undone.
     * Anything short of an authoritative answer — bot not connected yet, guild not available, request failed — leaves the
     * row alone instead of treating the user as gone. It used to work the other way round: an unresolved profile was
     * written back with the discord id as the name, and the purge deleted every row where name equalled id, so the few
     * ticks that run before the bot is ready were enough to wipe real users on every restart.
     */
    override suspend fun refresh(stale: DiscordUserInfo) {
        // In debug the bot is not connected to the guild, so no lookup here can conclude anything.
        if (Config.get("debug").toBoolean()) {
            DiscordDatabaseAccessor.touchUser(stale)
            return
        }

        // Throwing hands the row to markAsError, which rotates the queue without touching left_server_since.
        val jda = discordBot.jda ?: throw IllegalStateException("JDA is not ready yet")
        val guildId = Config.get("bot.guild.id")
        val guild = jda.getGuildById(guildId) ?: throw IllegalStateException("Guild $guildId is not available")

        // useCache(false) forces the REST call: a member missing from the cache is not a member who left.
        val member = try {
            guild.retrieveMemberById(stale.discordId).useCache(false).submit().await()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse in GONE_FROM_GUILD) {
                log(TAG, "refresh ${stale.discordId} confirmed gone from the guild (${e.errorResponse})")
                DiscordDatabaseAccessor.markAsLeftServer(stale)
                return
            }
            throw e
        }

        DiscordDatabaseAccessor.updateUser(
            DiscordUserInfo(
                discordId = stale.discordId,
                discordName = member.effectiveName,
                discordAvatar = member.user.effectiveAvatarUrl,
                updated = Date(),
                error = false
            )
        )
    }

    companion object {
        /** The only two answers that mean the user really is not in the guild any more. */
        private val GONE_FROM_GUILD = setOf(ErrorResponse.UNKNOWN_MEMBER, ErrorResponse.UNKNOWN_USER)
    }
}
