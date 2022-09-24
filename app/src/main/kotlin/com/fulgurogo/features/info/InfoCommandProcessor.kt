package com.fulgurogo.features.info

import com.fulgurogo.Config
import com.fulgurogo.features.bot.Command
import com.fulgurogo.features.bot.CommandProcessor
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import java.text.DecimalFormat

/**
 * Command processor for `/fulguro info` commands. (ex: `/fulguro info profil utilisateur:@ObiWanKenobi`)
 */
class InfoCommandProcessor : CommandProcessor {
    override fun processCommand(event: SlashCommandEvent, scanner: GameScanner?) {
        log(INFO, "processCommand ${event.name} ${event.subcommandName}")

        when (event.subcommandName) {
            Command.Info.Help.subcommandData.name ->
                event.scanCheck(Command.Info.Help, scanner?.isScanning, this::help)

            Command.Info.Link.subcommandData.name ->
                event.scanCheck(Command.Info.Link, scanner?.isScanning, this::link)

            Command.Info.Clean.subcommandData.name ->
                event.scanCheck(Command.Info.Clean, scanner?.isScanning, this::clean)

            Command.Info.Profile.subcommandData.name ->
                event.scanCheck(Command.Info.Profile, scanner?.isScanning, this::profile)

            else -> processUnknownCommand(event)
        }
    }

    override fun processUnknownCommand(event: SlashCommandEvent) {
        log(INFO, "processUnknownCommand ${event.name} ${event.subcommandName}")

        simpleError(
            acknowledge(event),
            Command.Info.EMOJI,
            """Commande inconnue. Consulte l'aide avec **/${Command.Fulguro.NAME} ${Command.Info.NAME} ${Command.Info.Help.subcommandData.name}**"""
        )
    }

    private fun help(event: SlashCommandEvent) {
        log(INFO, "help")

        simpleMessage(
            acknowledge(event),
            Command.Info.EMOJI,
            Command.Info.TITLE,
            "FulguroBot te permet d'associer tes comptes de serveurs de Go à ton profil Discord.\n\n${
                Command.Info.COMMANDS.map { it.helpString(Command.Info.NAME) }.reduce { a, b -> "$a\n$b" }
            }\n\n:information_source: __Où trouver mes id ?__\nPour lier ton compte sur un serveur de Go tu auras besoin de ton id.\n\n${
                UserAccount.LINKABLE_ACCOUNTS.map { it.help }.reduce { a, b -> "$a\n$b" }
            }\n\n:computer: __Que deviennent mes données ?__\nSeuls tes id de serveurs sont stockés, les autres données (nom, pseudo, rang etc...) sont récupérées depuis les API publiques des serveurs respectifs.")
    }

    private fun link(event: SlashCommandEvent) {
        log(INFO, "link")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        // It is safe to force args values (!!) here as they are required or command won't be evaluated
        val account = UserAccount.find(event.getOption(Command.Info.ACCOUNT_ARG)!!.asString)!!
        val accountId = event.getOption(Command.Info.ACCOUNT_ID_ARG)!!.asString

        DatabaseAccessor.user(account, accountId)?.let {
            simpleError(hook, Command.Info.EMOJI, "Le compte ${account.name} **$accountId** est déjà lié.")
        } ?: run {
            val user = account.client.user(User.dummyFrom(event.user.id, account, accountId))
            if (user?.id().isNullOrBlank())
                simpleError(
                    hook,
                    Command.Info.EMOJI,
                    "Impossible de trouver le compte ${account.name} **$accountId**."
                )
            else {
                val realId = if (account == UserAccount.FOX) user?.pseudo()!! else user?.id()!!
                DatabaseAccessor.linkUserAccount(event.user.id, account, realId)
                simpleMessage(
                    hook,
                    Command.Info.EMOJI,
                    Command.Info.TITLE,
                    ":white_check_mark: Le compte ${account.name} **$realId** est désormais associé à ton profil Discord !"
                )
            }
        }
    }

    private fun clean(event: SlashCommandEvent) {
        log(INFO, "clean")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)
        DatabaseAccessor.deleteUser(event.user.id)
        simpleMessage(
            hook,
            Command.Info.EMOJI,
            Command.Info.TITLE,
            ":white_check_mark: Tes informations ont bien été supprimées."
        )
    }

    private fun profile(event: SlashCommandEvent) {
        log(INFO, "profile")

        val hook = acknowledge(event)
        DatabaseAccessor.ensureUser(event.user)

        val targetUser = event.getOption(Command.USER_ARG)!!.asUser
        if (targetUser.isBot) {
            simpleError(hook, Command.Info.EMOJI, "FulguroBot ne peut dévoiler les infos des bots. :robot:")
        } else {
            val user = DatabaseAccessor.ensureUser(targetUser)

            val guild = event.guild!!
            val member = guild.getMember(targetUser)!!

            val msgBuilder = EmbedBuilder()
                .setColor(member.color)
                .setTitle(targetUser.name)
                .setThumbnail(targetUser.effectiveAvatarUrl)
                .setFooter("Membre ${guild.name} depuis ${member.timeJoined.year}")

            addFields(msgBuilder, user)
            addGames(msgBuilder, user)
            hook.sendMessageEmbeds(msgBuilder.build()).queue()
        }
    }

    private fun addFields(msgBuilder: EmbedBuilder, user: User) {
        // Add account fields
        UserAccount.LINKABLE_ACCOUNTS.forEach { account ->
            account.client.user(user)?.let {
                addEmbedField(msgBuilder, "${account.name} (${it.rank()})", it.link(false))
            }
        }

        // Add GOLD field
        DatabaseAccessor.ladderPlayer(user)?.let {
            val rank = it.rating.toRank().rankToString(false)
            val link = "${Config.Ladder.WEBSITE_URL}/players/${it.discordId}"
            addEmbedField(msgBuilder, "GOLD ($rank)", "[${user.name}]($link)")
        }

        // Add titles field
        addEmbedField(msgBuilder, "Titres", user.titles)

        // If user is hunter, add hunter specs
        val title = DatabaseAccessor.ensureExamPlayer(user).title()
        if (title.isNotBlank()) addEmbedField(msgBuilder, "Examen Hunter", title)
    }

    private fun addGames(msgBuilder: EmbedBuilder, user: User) {
        // Fetch all games
        val games = DatabaseAccessor.infoGamesFor(user.discordId)

        val wins = games.count { it.finished && it.mainPlayerWon == true }
        val losses = games.count { it.finished && it.mainPlayerWon == false }
        val ratioString = if (games.isNotEmpty()) {
            val ratio = wins.toFloat() * 100 / (wins + losses).toFloat()
            "(**" + DecimalFormat("#.#").format(ratio.toDouble()) + "%** de victoires)"
        } else ""

        var message = "**${games.size}** parties jouées\n**$wins** victoires / **$losses** défaites $ratioString"

        if (games.isNotEmpty()) {
            message += "\n\n__Parties récentes :__\n"
            games
                .sortedByDescending { it.date }
                .take(15)
                .forEach {
                    var description = ""
                    description += if (!it.finished) ":timer:" else if (it.mainPlayerWon == true) ":white_check_mark:" else ":x:"
                    description += " [${it.server}](${it.gameLink()}) "
                    description += " " + if (it.mainPlayerIsBlack == true) ":black_circle:" else ":white_circle:"
                    description += " " + (if (it.handicap == 0) "`VS`" else it.handicap?.toHandicapEmoji()) + " "
                    description += " " + if (it.mainPlayerIsBlack == true) ":white_circle:" else ":black_circle:"
                    description += " ${it.opponentLink()} "
                    message += "$description\n"
                }
        }
        msgBuilder.setDescription(message)
    }
}
