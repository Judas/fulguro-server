package com.fulgurogo.features.bot

import com.fulgurogo.TAG
import com.fulgurogo.common.logger.log
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.utilities.acknowledge
import com.fulgurogo.utilities.simpleError
import com.fulgurogo.utilities.simpleMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

object FulguroBot : ListenerAdapter() {
    var jda: JDA? = null

    /**
     * When ready, create slash commands and start services.
     */
    override fun onReady(event: ReadyEvent) {
        super.onReady(event)
        log(TAG, "onReady")

        jda = event.jda

        // Add bot commands to guild
        val command = Commands.slash("fulguro", "Commandes Admin de FulguroBot")
            .addSubcommands(SubcommandData("scan", "Lance un scan des parties."))
        for (guild in event.jda.guilds) {
            guild.updateCommands().addCommands(command).queue()
        }
    }

    /**
     * Release resources on shutdown
     */
    override fun onShutdown(event: ShutdownEvent) {
        super.onShutdown(event)
        log(TAG, "onShutdown")
        jda = null
    }

    /**
     * When receiving a slash command, forward to the appropriate command processor.
     */
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        super.onSlashCommandInteraction(event)
        log(TAG, "onSlashCommandInteraction ${event.name} ${event.subcommandName}")

        if (event.name == "fulguro" && event.subcommandName == "scan")
            scan(event)
        else simpleError(
            acknowledge(event),
            ":robot:",
            "Commande inconnue."
        )
    }

    private fun scan(event: SlashCommandInteractionEvent) {
        log(TAG, "scan")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user.id)

        val asimov = event.guild?.getMember(event.user)?.roles?.any { it.name == "Asimov" } ?: false
        if (asimov) {
            CoroutineScope(Dispatchers.IO).launch {
                log(TAG, "Starting manual scan.")
                GameScanner.scan()
            }
            simpleMessage(hook, ":robot:", "Scan manuel", "Scan démarré")
        } else simpleError(hook, ":robot:", ":robot: *Commande réservée aux admins*")
    }
}
