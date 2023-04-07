package com.fulgurogo.features.bot

import com.fulgurogo.Config.Bot
import com.fulgurogo.features.admin.AdminCommandProcessor
import com.fulgurogo.features.exam.ExamCommandProcessor
import com.fulgurogo.features.exam.ExamService
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.features.info.InfoCommandProcessor
import com.fulgurogo.features.ladder.LadderCommandProcessor
import com.fulgurogo.features.ladder.LadderService
import com.fulgurogo.features.user.UserService
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.ShutdownEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.RestAction
import java.util.*

class FulguroBot : ListenerAdapter() {
    private var gameScanner: GameScanner? = null

    private var mainCommandProcessor: MainCommandProcessor = MainCommandProcessor()
    private val subcommandProcessors: MutableMap<String, CommandProcessor> = mutableMapOf(
        Command.Admin.NAME to AdminCommandProcessor(),
        Command.Info.NAME to InfoCommandProcessor(),
        Command.Exam.NAME to ExamCommandProcessor(),
        Command.Ladder.NAME to LadderCommandProcessor()
    )

    /**
     * When ready, create slash commands and start services.
     */
    override fun onReady(event: ReadyEvent) {
        super.onReady(event)
        log(INFO, "onReady")

        // Start game scanner
        gameScanner = GameScanner(
            listOf(
                UserService(event.jda),
                ExamService(event.jda),
                LadderService(),
            )
        )
        gameScanner?.start()

        // Add bot commands to guild
        for (guild in event.jda.guilds) {
            guild.updateCommands().addCommands(Command.Fulguro.GROUP).queue()
        }
    }

    /**
     * Release resources on shutdown
     */
    override fun onShutdown(event: ShutdownEvent) {
        super.onShutdown(event)
        log(INFO, "onShutdown")

        // Stop game scanner
        gameScanner?.stop()
    }

    /**
     * When receiving a slash command, forward to the appropriate command processor.
     */
    override fun onSlashCommand(event: SlashCommandEvent) {
        super.onSlashCommand(event)
        log(INFO, "onSlashCommand ${event.name} ${event.subcommandName}")
        if (event.name == Command.Fulguro.NAME) {
            (subcommandProcessors[event.subcommandGroup] ?: mainCommandProcessor)
                .processCommand(event, gameScanner)
        } else mainCommandProcessor.processUnknownCommand(event)
    }

    /**
     * When receiving a malformed command, delete it and send a private message to the author.
     */
    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        super.onGuildMessageReceived(event)

        val originalMessage = event.message.contentDisplay
        val trimmed = originalMessage.trim().replace(" ".toRegex(), "").lowercase(Locale.ROOT)
        if (trimmed.startsWith("/" + Command.Fulguro.NAME)) {
            log(INFO, "Filtering command $originalMessage")

            val embed = EmbedBuilder().setColor(Bot.EMBED_COLOR)
                .setTitle(Command.Fulguro.EMOJI + "    __Commandes FulguroBot__    " + Command.Fulguro.EMOJI)
                .setDescription("Ta commande était malformée, je l'ai supprimée du salon. Voilà ce que tu as tapé : \n**$originalMessage**")
                .build()

            val deleteOriginal: RestAction<Void> = event.message.delete()
            val sendPM = event.author.openPrivateChannel().flatMap { channel -> channel.sendMessageEmbeds(embed) }
            deleteOriginal.and(sendPM).queue()
        }
    }

    override fun onPrivateMessageReceived(event: PrivateMessageReceivedEvent) {
        super.onPrivateMessageReceived(event)
        log(INFO, "onPrivateMessageReceived")
    }
}
