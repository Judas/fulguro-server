package com.fulgurogo.discord

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.ellipsize
import com.fulgurogo.discord.DiscordModule.TAG
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
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

    fun sendMessageEmbeds(channelId: String, message: String, title: String = "") = jda
        ?.getTextChannelById(channelId)
        ?.sendMessageEmbeds(
            EmbedBuilder()
                .setColor(Color.decode(Config.get("bot.color")))
                .apply { if (title.isNotBlank()) setTitle(title) }
                .setDescription(message.ellipsize(2048))
                .build()
        )
        ?.queue()

    fun sendMessage(channelId: String, message: String) = jda
        ?.getTextChannelById(channelId)
        ?.sendMessage(message)
        ?.queue()
}
