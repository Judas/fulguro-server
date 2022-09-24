package com.fulgurogo.features.kitchen

import com.fulgurogo.features.bot.Command
import com.fulgurogo.features.bot.CommandProcessor
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import java.util.*

/**
 * Command processor for `/fulguro chef` commands. (ex: `/fulguro chef cuisiner recette:tartiflette`)
 */
class KitchenCommandProcessor : CommandProcessor {
    override fun processCommand(event: SlashCommandEvent, scanner: GameScanner?) {
        log(INFO, "processCommand ${event.name} ${event.subcommandName}")

        when (event.subcommandName) {
            Command.Kitchen.Help.subcommandData.name ->
                event.scanCheck(Command.Kitchen.Help, scanner?.isScanning, this::help)

            Command.Kitchen.Cook.subcommandData.name ->
                event.scanCheck(Command.Kitchen.Cook, scanner?.isScanning, this::cook)

            else -> processUnknownCommand(event)
        }
    }

    override fun processUnknownCommand(event: SlashCommandEvent) {
        log(INFO, "processUnknownCommand ${event.name} ${event.subcommandName}")

        simpleError(
            acknowledge(event),
            Command.Kitchen.EMOJI,
            "Commande inconnue. Consulte l'aide avec **/${Command.Fulguro.NAME} ${Command.Kitchen.NAME} ${Command.Kitchen.Help.subcommandData.name}**"
        )
    }

    private fun help(event: SlashCommandEvent) {
        log(INFO, "help")

        simpleMessage(
            acknowledge(event),
            Command.Kitchen.EMOJI,
            Command.Kitchen.TITLE,
            "FulguroBot peut t'aider à choisir ton prochain repas.\n\n${
                Command.Kitchen.COMMANDS.map { it.helpString(Command.Kitchen.NAME) }.reduce { a, b -> "$a\n$b" }
            }")
    }

    private fun cook(event: SlashCommandEvent) {
        log(INFO, "cook")

        val meal = event.getOption(Command.Kitchen.MEAL_ARG)?.asString?.lowercase(Locale.getDefault()).orEmpty()
        val message = when {
            meal.contains("frites") || meal.contains("fries") -> ":fries: Très bon choix. Bon appétit !"
            meal.contains("tajine") -> "<:TAJINE:774294276665245727> Très bon choix. Bon appétit !"
            else -> "Prépare plutôt des :fries: ou un <:TAJINE:774294276665245727> !"
        }
        simpleMessage(acknowledge(event), Command.Kitchen.EMOJI, Command.Kitchen.TITLE, message)
    }
}
