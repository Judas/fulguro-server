package com.fulgurogo.features.admin

import com.fulgurogo.features.bot.Command
import com.fulgurogo.features.bot.CommandProcessor
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.features.league.LeagueService
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

/**
 * Command processor for `/fulguro admin` commands. (ex: `/fulguro admin scan`)
 */
class AdminCommandProcessor : CommandProcessor {
    override fun processCommand(event: SlashCommandEvent, scanner: GameScanner?) {
        log(INFO, "processCommand ${event.name} ${event.subcommandName}")

        when (event.subcommandName) {
            Command.Admin.Scan.subcommandData.name -> event.scanCheck(
                Command.Admin.Scan,
                scanner?.isScanning
            ) { scan(it, scanner) }

            Command.Admin.Oteai.subcommandData.name -> event.scanCheck(
                Command.Admin.Oteai,
                scanner?.isScanning,
                this::oteai
            )

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

    private fun scan(event: SlashCommandEvent, gameScanner: GameScanner?) {
        log(INFO, "scan")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        val asimov = event.guild?.getMember(event.user)?.roles?.any { it.name == "Asimov" } ?: false
        if (asimov) {
            CoroutineScope(Dispatchers.IO).launch {
                log(INFO, "Starting manual scan.")
                gameScanner?.scan()
            }
            simpleMessage(hook, ":robot:", "Scan manuel", "Scan démarré")
        } else simpleError(hook, Command.Exam.EMOJI, ":robot: *Commande réservée aux admins*")
    }

    private fun oteai(event: SlashCommandEvent) {
        log(INFO, "oteai")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        val asimov = event.guild?.getMember(event.user)?.roles?.any { it.name == "Asimov" } ?: false
        if (asimov) {
            CoroutineScope(Dispatchers.IO).launch {
                log(INFO, "Starting manual oteai rush draw.")
                LeagueService(event.jda).drawPairings()
            }
            simpleMessage(hook, ":robot:", "Tirage manuel", "Tirage lancé")
        } else simpleError(hook, Command.Exam.EMOJI, ":robot: *Commande réservée aux admins*")
    }
}
