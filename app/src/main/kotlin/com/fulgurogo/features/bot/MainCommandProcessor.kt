package com.fulgurogo.features.bot

import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

/**
 * Command processor for `/fulguro` top-level commands. (ex: `/fulguro help`)
 */
class MainCommandProcessor() : CommandProcessor {
    override fun processCommand(event: SlashCommandEvent, scanner: GameScanner?) {
        log(INFO, "processCommand ${event.name} ${event.subcommandName}")

        when (event.subcommandName) {
            Command.Fulguro.Help.subcommandData.name ->
                event.scanCheck(Command.Fulguro.Help, scanner?.isScanning, this::help)

            else -> processUnknownCommand(event)
        }
    }

    override fun processUnknownCommand(event: SlashCommandEvent) {
        log(INFO, "processUnknownCommand ${event.name} ${event.subcommandName}")

        simpleError(
            acknowledge(event),
            Command.Fulguro.EMOJI,
            "Commande inconnue. Consulte l'aide avec **/${Command.Fulguro.NAME} ${Command.Fulguro.Help.subcommandData.name}**"
        )
    }

    private fun help(event: SlashCommandEvent) {
        log(INFO, "help")

        simpleMessage(
            acknowledge(event),
            Command.Fulguro.EMOJI,
            Command.Fulguro.TITLE,
            "FulguroBot regroupe plusieurs séries de commandes." +
                    "\n\n__Commandes d'informations :__" +
                    "\nCes commandes te permettent de lier tes comptes des différents serveurs de go à ton profil Discord." +
                    "\nElles servent aussi à afficher le profil / les parties des autres utilisateurs." +
                    "\nPour plus d'informations tape : **/${Command.Fulguro.NAME} ${Command.Info.NAME} ${Command.Info.Help.subcommandData.name}**" +
                    "\n\n__Commandes de l'**Examen Hunter** :__" +
                    "\nCes commandes te servent pour l'évènement permanent de l'Examen Hunter." +
                    "\nPour plus d'informations tape : **/${Command.Fulguro.NAME} ${Command.Exam.NAME} ${Command.Exam.Help.subcommandData.name}**" +
                    "\n\n__Commandes de l'**Echelle Gold** :__" +
                    "\nCes commandes servent pour l'Echelle Gold." +
                    "\nPour plus d'informations tape : **/${Command.Fulguro.NAME} ${Command.Ladder.NAME} ${Command.Ladder.Help.subcommandData.name}**"
        )
    }
}
