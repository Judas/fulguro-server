package com.fulgurogo.features.ladder

import com.fulgurogo.Config
import com.fulgurogo.features.bot.Command
import com.fulgurogo.features.bot.CommandProcessor
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

/**
 * Command processor for `/fulguro gold` commands. (ex: `/fulguro gold classement`)
 */
class LadderCommandProcessor : CommandProcessor {
    private val playStrings = listOf(
        "%player% est prêt à en découdre. Qui osera le défier ?",
        "C'est l'heure du du-du-du-duel contre %player% !",
        "%player% est en chasse, qui sera sa proie ce soir ?",
        "Vite un adversaire pour %player% !",
        "Qui pour affronter %player%, toi peut-être ?",
        "Trou noir ? Sainte-Croix ? Grande Muraille ? %player% est prêt à tout jouer, viens le défier !",
        "Grande réduction sur les tobi vers le centre ! Défie %player% dès maintenant pour profiter de l'offre.",
        "%player% ne croit pas qu'il y ait de bonne ou de mauvaise forme, le Go c'est d'abord une affaire de rencontre. Alors rencontre le sur le goban.",
        "La rumeur circule comme quoi si tu affrontes %player%, Larenty va jouer au Go contre toi.",
        "%player% t'attend dans le couloir à la récré pour de la baston.",
        "%player% cherche victime pour sacrifice rituel dans le grand temple des douleurs.",
        "\"Il te faut affronter %player% si tu veux te rapprocher du One Piece !\" **Gol D. Ladder**",
        "%player% a une pierre avec ton nom écrit dessus. Viens la capturer si tu l'oses.",
        "Qui souhaite se délecter d'une partie de go en la présence de %player% ?",
        "Toi aussi viens tenter de sauver Odette en jouant contre %player% !",
    )

    override fun processCommand(event: SlashCommandEvent, scanner: GameScanner?) {
        log(INFO, "processCommand ${event.name} ${event.subcommandName}")

        when (event.subcommandName) {
            Command.Ladder.Help.subcommandData.name ->
                event.scanCheck(Command.Ladder.Help, scanner?.isScanning, this::help)

            Command.Ladder.Play.subcommandData.name ->
                event.scanCheck(Command.Ladder.Play, scanner?.isScanning, this::play)

            else -> processUnknownCommand(event)
        }
    }

    override fun processUnknownCommand(event: SlashCommandEvent) {
        log(INFO, "processUnknownCommand ${event.name} ${event.subcommandName}")
        simpleError(acknowledge(event), Command.Ladder.EMOJI, "Commande inconnue.")
    }

    private fun help(event: SlashCommandEvent) {
        log(INFO, "help")

        simpleMessage(
            acknowledge(event),
            Command.Ladder.EMOJI,
            Command.Ladder.TITLE,
            "FulguroBot te permet de faire partie de l'Echelle Gold.\n\n${
                Command.Ladder.COMMANDS.map { it.helpString(Command.Ladder.NAME) }.reduce { a, b -> "$a\n$b" }
            }")
    }

    private fun play(event: SlashCommandEvent) {
        log(INFO, "play")

        val hook = acknowledge(event)

        val fulguroUser = DatabaseAccessor.ensureUser(event.user.id)
        DatabaseAccessor.apiLadderPlayer(fulguroUser.discordId)?.let {
            val ogsLink =
                if (fulguroUser.ogsId.isNullOrBlank().not())
                    "[OGS](${Config.Ogs.WEBSITE_URL}/player/${fulguroUser.ogsId})"
                else null
            val kgsLink =
                if (fulguroUser.kgsId.isNullOrBlank().not())
                    "[KGS](${Config.Kgs.GRAPH_URL}?user=${fulguroUser.kgsId})"
                else null
            val goldLink =
                "[**${event.jda.userName(it.discordId)} (${it.tierName})**](${Config.Ladder.WEBSITE_URL}/players/${it.discordId})"
            var links = ""
            if (ogsLink != null) links += ogsLink
            if (kgsLink != null) {
                if (links.isNotEmpty()) links += " - "
                links += kgsLink
            }
            val message = playStrings.random().replace("%player%", goldLink)
            simpleMessage(hook, Command.Ladder.EMOJI, "Échelle Gold", "Message envoyé !")
            event.jda.publicMessage(
                Config.Ladder.PLAY_CHANNEL,
                "${Command.Ladder.EMOJI} $message :crossed_swords:\n$links"
            )
        } ?: run {
            simpleError(hook, Command.Ladder.EMOJI, "Tu ne fais pas partie de l'Échelle Gold.")
        }
    }
}
