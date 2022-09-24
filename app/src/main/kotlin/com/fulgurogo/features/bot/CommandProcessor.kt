package com.fulgurogo.features.bot

import com.fulgurogo.features.games.GameScanner
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

interface CommandProcessor {
    fun processCommand(event: SlashCommandEvent, scanner: GameScanner?)
    fun processUnknownCommand(event: SlashCommandEvent)
}
