package com.fulgurogo.features.bot

import com.fulgurogo.features.user.UserAccount
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData

/**
 * Bot commands and subcommands.
 */
sealed class Command(val emoji: String, val subcommandData: SubcommandData, val scanCompatible: Boolean = true) {
    companion object {
        const val USER_ARG = "utilisateur"

        private fun userOption(): OptionData = OptionData(
            OptionType.USER, USER_ARG, "Un utilisateur du serveur Discord FulguroGo."
        ).setRequired(true)
    }

    fun helpString(group: String): String =
        "$emoji **/${Fulguro.NAME} $group ${subcommandData.name}** : ${subcommandData.description}"

    sealed class Fulguro {
        companion object {
            const val NAME = "fulguro"
            const val EMOJI = ":robot:"
            const val TITLE = "FulguroBot"
            const val DESC = "Les commandes du bot FulguroGo"
            val COMMANDS = listOf(Help)
            val GROUP = CommandData(NAME, DESC)
                .addSubcommands(COMMANDS.map { it.subcommandData })
                .addSubcommandGroups(Info.GROUP, Kitchen.GROUP, Exam.GROUP, Ladder.GROUP, Admin.GROUP)
        }

        object Help : Command(
            ":question:",
            SubcommandData("aide", "Affiche l'aide du bot.")
        )
    }

    sealed class Info {
        companion object {
            const val NAME = "info"
            const val EMOJI = ":information_source:"
            const val TITLE = "Informations joueur"
            const val DESC = "Commandes d'informations utilisateur"
            val COMMANDS = listOf(Help, Link, Clean, Profile)
            val GROUP = SubcommandGroupData(NAME, DESC).addSubcommands(COMMANDS.map { it.subcommandData })

            const val ACCOUNT_ID_ARG = "identifiant"
            const val ACCOUNT_ARG = "serveur"

            private fun accountOption(): OptionData = OptionData(
                OptionType.STRING, ACCOUNT_ARG, "Un des serveurs de go."
            ).addChoices(UserAccount.LINKABLE_ACCOUNT_CHOICES).setRequired(true)

            private fun accountIdOption(): OptionData = OptionData(
                OptionType.STRING,
                ACCOUNT_ID_ARG,
                "L'identifiant de ton compte sur le serveur choisi (plus d'infos dans l'aide)."
            ).setRequired(true)
        }

        object Help : Command(
            ":question:", SubcommandData(
                "aide", "Affiche l'aide des commandes d'informations."
            )
        )

        object Link : Command(
            ":link:", SubcommandData(
                "lier", "Associe ton compte sur un serveur de Go à ton profil Discord."
            ).addOptions(accountOption(), accountIdOption()),
            false
        )

        object Clean : Command(
            ":broom:", SubcommandData(
                "supprimer", "Supprime tous les liens associés à ton profil Discord."
            )
        )

        object Profile : Command(
            ":notepad_spiral:", SubcommandData(
                "profil", "Affiche les informations d'un utilisateur."
            ).addOptions(userOption()),
            false
        )
    }

    sealed class Kitchen {
        companion object {
            const val NAME = "chef"
            const val EMOJI = ":cook:"
            const val TITLE = "Cuisine Grottesque"
            const val DESC = "Commandes de la Cuisine Grottesque"
            val COMMANDS = listOf(Help, Cook)
            val GROUP = SubcommandGroupData(NAME, DESC).addSubcommands(COMMANDS.map { it.subcommandData })

            const val MEAL_ARG = "plat"

            private fun recipeOption(): OptionData = OptionData(
                OptionType.STRING, MEAL_ARG, "Le plat à préparer"
            ).setRequired(true)
        }

        object Help : Command(
            ":question:", SubcommandData(
                "aide", "Affiche l'aide des commandes de la Cuisine Grottesque."
            )
        )

        object Cook : Command(
            ":fork_and_knife:", SubcommandData(
                "cuisiner", "Prépare un plat."
            ).addOptions(recipeOption())
        )
    }

    sealed class Exam {
        companion object {
            const val NAME = "exam"
            const val EMOJI = ":crossed_swords:"
            const val TITLE = "Examen Hunter"
            const val DESC = "Commandes de l'Examen Hunter"
            val COMMANDS = listOf(Help, Ranking, Position, Hunters, Leaders, Stats)
            val GROUP = SubcommandGroupData(NAME, DESC).addSubcommands(COMMANDS.map { it.subcommandData })
        }

        object Ranking : Command(
            ":trophy:", SubcommandData(
                "classement", "Affiche le top 20 du classement de l'examen en cours."
            )
        )

        object Position : Command(
            ":question:", SubcommandData(
                "position", "Affiche la position d'un joueur dans le classement de l'examen en cours."
            ).addOptions(userOption())
        )

        object Hunters : Command(
            ":military_medal:", SubcommandData(
                "hunters", "Affiche la liste des Hunters diplômés."
            )
        )

        object Leaders : Command(
            ":first_place:", SubcommandData(
                "leaders", "Affiche la liste des leaders de chaque catégorie."
            )
        )

        object Stats : Command(
            ":bar_chart:", SubcommandData(
                "stats", "Affiche les statistiques de la promotion."
            )
        )

        object Help : Command(
            ":question:", SubcommandData(
                "aide", "Affiche l'aide des commandes de l'Examen Hunter."
            )
        )
    }

    sealed class Ladder {
        companion object {
            const val NAME = "gold"
            const val EMOJI = "<:goldladder:1005387366673948755>"
            const val TITLE = "Échelle Gold"
            const val DESC = "Commandes de l'Échelle Gold"
            val COMMANDS = listOf(Help, Play)
            val GROUP = SubcommandGroupData(NAME, DESC).addSubcommands(COMMANDS.map { it.subcommandData })
        }

        object Play : Command(
            ":crossed_swords:", SubcommandData(
                "play", "Annonce sur le serveur que tu es prêt à jouer une partie."
            )
        )

        object Help : Command(
            ":question:", SubcommandData(
                "aide", "Affiche l'aide des commandes de l'Echelle Gold."
            )
        )
    }

    sealed class Admin {
        companion object {
            const val NAME = "admin"
            const val EMOJI = ":robot:"
            const val TITLE = "Administration"
            const val DESC = "Commandes admin du Bot"
            val COMMANDS = listOf(Scan, Oteai)
            val GROUP = SubcommandGroupData(NAME, DESC).addSubcommands(COMMANDS.map { it.subcommandData })
        }

        object Scan : Command(
            ":mag:",
            SubcommandData("scan", "Force un scan des parties."),
            false
        )

        object Oteai : Command(
            ":robot:",
            SubcommandData("oteai", "Force un tirage Oteai."),
            false
        )
    }
}
