package com.fulgurogo.features.exam

import com.fulgurogo.features.bot.Command
import com.fulgurogo.features.bot.CommandProcessor
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Command processor for `/fulguro exam` commands. (ex: `/fulguro exam classement`)
 */
class ExamCommandProcessor : CommandProcessor {
    override fun processCommand(event: SlashCommandEvent, scanner: GameScanner?) {
        log(INFO, "processCommand ${event.name} ${event.subcommandName}")

        when (event.subcommandName) {
            Command.Exam.Help.subcommandData.name ->
                event.scanCheck(Command.Exam.Help, scanner?.isScanning, this::help)

            Command.Exam.Ranking.subcommandData.name ->
                event.scanCheck(Command.Exam.Ranking, scanner?.isScanning, this::ranking)

            Command.Exam.Position.subcommandData.name ->
                event.scanCheck(Command.Exam.Position, scanner?.isScanning, this::position)

            Command.Exam.Hunters.subcommandData.name ->
                event.scanCheck(Command.Exam.Hunters, scanner?.isScanning, this::hunters)

            Command.Exam.Leaders.subcommandData.name ->
                event.scanCheck(Command.Exam.Leaders, scanner?.isScanning, this::leaders)

            Command.Exam.Stats.subcommandData.name ->
                event.scanCheck(Command.Exam.Stats, scanner?.isScanning, this::stats)

            else -> processUnknownCommand(event)
        }
    }

    override fun processUnknownCommand(event: SlashCommandEvent) {
        log(INFO, "processUnknownCommand ${event.name} ${event.subcommandName}")
        simpleError(acknowledge(event), Command.Exam.EMOJI, "Commande inconnue.")
    }

    private fun help(event: SlashCommandEvent) {
        log(INFO, "help")

        simpleMessage(
            acknowledge(event),
            Command.Exam.EMOJI,
            Command.Exam.TITLE,
            "FulguroBot te permet de participer à l'évènement permanent intitulé Examen Hunter.\n\n${
                Command.Exam.COMMANDS.map { it.helpString(Command.Exam.NAME) }.reduce { a, b -> "$a\n$b" }
            }")
    }

    private fun ranking(event: SlashCommandEvent) {
        log(INFO, "ranking")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        var message = ""
        DatabaseAccessor.examPlayers()
            .filter { it.totalPoints() > 0 }
            .sortedWith(ExamPlayerComparator())
            .take(20)
            .forEachIndexed { index, hunter ->
                message += "\n**${index + 1}.** ${event.jda.userName(hunter)} *(${hunter.totalPointsString()})*${hunter.title()}"
            }

        if (message.isNotBlank()) simpleMessage(hook, Command.Exam.EMOJI, "Classement de l'**Examen Hunter**", message)
        else simpleError(hook, Command.Exam.EMOJI, "*Aucun participant n'a de points*")
    }

    private fun position(event: SlashCommandEvent) {
        log(INFO, "position")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        val targetUser = event.getOption(Command.USER_ARG)!!.asUser
        if (targetUser.isBot) {
            simpleError(hook, Command.Exam.EMOJI, "FulguroBot ne peut dévoiler les infos des bots. :robot:")
            return
        }

        val allHunters = DatabaseAccessor.examPlayers()
        val hunters = allHunters.sortedWith(ExamPlayerComparator())
        val index = hunters.indexOfFirst { it.discordId == targetUser.id }
        if (index == -1) {
            simpleError(hook, Command.Exam.EMOJI, "Ce joueur ne participe pas à l'**Examen Hunter** !")
        } else {
            val hunter = hunters[index]
            var message =
                "**${index + 1}.** ${event.jda.userName(hunter)} *(${hunter.totalPointsString()})*${hunter.title()}\n"
            ExamSpecialization.values().forEach { spec ->
                val specHunters = allHunters.sortedWith(ExamPlayerComparator(spec))
                val specIndex = specHunters.indexOfFirst { it.discordId == targetUser.id }
                message += "\n${spec.emoji} **${specIndex + 1}.** ${spec.type} : ${spec.pointsStringCallback(hunter)}"
            }

            if (hunter.phantom > 0) message += "\n:ghost: Brigade : ${hunter.phantom}pts"

            simpleMessage(hook, Command.Exam.EMOJI, Command.Exam.TITLE, message)
        }
    }

    private fun hunters(event: SlashCommandEvent) {
        log(INFO, "hunters")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        var message = ""
        DatabaseAccessor
            .examPlayers()
            .filter { it.hunter }
            .forEach { message += "${event.jda.userName(it)}${it.title()}\n" }

        if (message.isNotBlank()) simpleMessage(hook, Command.Exam.EMOJI, Command.Exam.TITLE, message)
        else simpleError(hook, Command.Exam.EMOJI, "Il n'y a aucun hunter pour l'instant")
    }

    private fun leaders(event: SlashCommandEvent) {
        log(INFO, "leaders")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        val allHunters = DatabaseAccessor.examPlayers()
        val hunters = allHunters.sortedWith(ExamPlayerComparator())
        var message =
            "**${Command.Exam.EMOJI} Total :** ${event.jda.userName(hunters[0])} *(${hunters[0].totalPointsString()})*"
        ExamSpecialization.values().forEach { spec ->
            val specHunters = allHunters.sortedWith(ExamPlayerComparator(spec))
            message += "\n**${spec.emoji} ${spec.type} :** ${event.jda.userName(specHunters[0])} *(${
                spec.pointsStringCallback(specHunters[0])
            })*"
        }
        simpleMessage(hook, Command.Exam.EMOJI, "Leaders de l'Examen Hunter", message)
    }

    private fun stats(event: SlashCommandEvent) {
        log(INFO, "stats")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        val promoName = DateTimeFormatter
            .ofPattern("MMMM YYYY", Locale.FRANCE)
            .withLocale(Locale.FRANCE)
            .format(ZonedDateTime.now(DATE_ZONE))
            .replaceFirstChar { it.titlecase() }

        val stats = DatabaseAccessor.examStats()
        val award = DatabaseAccessor.examAward()

        var message =
            "Les **${stats.candidates} candidats** de la promotion **$promoName** ont cumulé **${stats.promoTotal} points** "
        message += "en jouant **${stats.gamesPlayed()} parties**, dont **${stats.internalGamesPlayed()}** entre membres de la promotion."
        message += "\n\n Le **Netero Award** est actuellement détenu par la promotion **${award.promo}** avec un score de ${award.score} points"

        simpleMessage(hook, Command.Exam.EMOJI, "Statistiques de la promotion $promoName", message)
    }
}