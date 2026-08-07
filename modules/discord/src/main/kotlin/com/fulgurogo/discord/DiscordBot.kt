package com.fulgurogo.discord

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.ellipsize
import com.fulgurogo.discord.DiscordModule.TAG
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color

class DiscordBot : ListenerAdapter() {
    var jda: JDA? = null

    override fun onReady(event: ReadyEvent) {
        super.onReady(event)
        log(TAG, "onReady")
        jda = event.jda
    }

    override fun onShutdown(event: ShutdownEvent) {
        super.onShutdown(event)
        log(TAG, "onShutdown")
        jda = null
    }

    fun sendMessageEmbeds(channelId: String, message: String, title: String = "", imageUrl: String = "") = jda
        ?.getTextChannelById(channelId)
        ?.sendMessageEmbeds(
            EmbedBuilder()
                .setColor(Color.decode(Config.get("bot.color")))
                .apply { if (title.isNotBlank()) setTitle(title) }
                .apply { if (imageUrl.isNotBlank()) setThumbnail(imageUrl) }
                .setAuthor(Config.get("bot.name"), Config.get("frontend.url"), Config.get("gold.default.avatar"))
                .setDescription(message.ellipsize(2048))
                .build()
        )
        ?.queue()

    /** Gives [roleId] to [discordId] on `bot.guild.id`. A no-op the member already has costs one wasted request. */
    fun addRole(discordId: String, roleId: String) = modifyRole(discordId, roleId, add = true)

    /** Takes [roleId] away from [discordId]. Discord answers 204 whether or not they had it, so this is idempotent. */
    fun removeRole(discordId: String, roleId: String) = modifyRole(discordId, roleId, add = false)

    /**
     * One member's role, added or removed, best-effort.
     *
     * Nothing here throws and nothing is reported back to the caller. A role is cosmetic — it decorates a membership that
     * is already written to the database — so a bot that is not connected yet, a role that was deleted or a hierarchy the
     * bot cannot reach must not turn into a failed join. Each of those is a log line instead, and the whole point of
     * spelling them out separately is that Discord answers all of them with the same silence.
     *
     * [UserSnowflake.fromId] rather than a member lookup: the id is all the REST call needs, so this works without the
     * member being in JDA's cache, and costs no extra request. The flip side is that a member who is not in the guild at
     * all fails at the queue rather than being caught here — which is the right way round, since `DiscordService` is the
     * one place allowed to conclude that someone has left.
     *
     * `canInteract` is checked up front because it is the one failure a person has to fix in Discord: a house role sitting
     * above the bot's own role in the list can never be granted, and reading that off a 403 in the log is a bad afternoon.
     *
     * The catch is what makes "nothing throws" true, and it is not belt-and-braces. JDA checks permissions **client-side
     * and synchronously**: with MANAGE_ROLES missing, `addRoleToMember` throws `InsufficientPermissionException` on this
     * thread before any request is queued, so the failure callback below never sees it. That callback only covers what
     * Discord answers. Measured, not deduced — without the catch, a join on a guild where the bot lacks the permission
     * wrote the membership, announced the arrival, and then answered the website 500.
     */
    private fun modifyRole(discordId: String, roleId: String, add: Boolean) {
        val what = if (add) "addRole" else "removeRole"
        val jda = jda
        if (jda == null) {
            log(TAG, "$what $discordId $roleId FAILURE bot is not connected")
            return
        }

        val guildId = Config.get("bot.guild.id")
        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            log(TAG, "$what $discordId $roleId FAILURE guild $guildId is not available")
            return
        }

        val role = guild.getRoleById(roleId)
        if (role == null) {
            log(TAG, "$what $discordId $roleId FAILURE unknown role on guild $guildId")
            return
        }

        if (!guild.selfMember.canInteract(role)) {
            log(TAG, "$what $discordId $roleId FAILURE role ${role.name} is above the bot's own role")
            return
        }

        try {
            val member = UserSnowflake.fromId(discordId)
            val action = if (add) guild.addRoleToMember(member, role) else guild.removeRoleFromMember(member, role)
            action.queue(
                { log(TAG, "$what $discordId ${role.name} OK") },
                { error -> log(TAG, "$what $discordId ${role.name} FAILURE", error) }
            )
        } catch (e: Exception) {
            log(TAG, "$what $discordId ${role.name} FAILURE", e)
        }
    }
}
