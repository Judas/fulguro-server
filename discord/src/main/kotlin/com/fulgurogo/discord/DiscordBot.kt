package com.fulgurogo.discord

import com.fulgurogo.common.logger.log
import com.fulgurogo.discord.DiscordModule.TAG
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

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
}
