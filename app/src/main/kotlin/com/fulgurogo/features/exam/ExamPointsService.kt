package com.fulgurogo.features.exam

import com.fulgurogo.Config
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.Game
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.JDA
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min

class ExamPointsService(private val jda: JDA) {
    companion object {
        private const val EMOJI = ":crossed_swords:"
    }

    fun refresh() {
        log(INFO, "refresh")

        log(INFO, "Clearing all exam points")
        DatabaseAccessor.clearExamPoints()

        val now = ZonedDateTime.now(DATE_ZONE)
        val promoName = DateTimeFormatter
            .ofPattern("MMMM YYYY", Locale.FRANCE)
            .withLocale(Locale.FRANCE)
            .format(now.minusMonths(1))
            .replaceFirstChar { it.titlecase() }

        val shouldCloseSession = !DatabaseAccessor.hasPromotionScore(promoName)
        if (shouldCloseSession) {
            log(INFO, "Closing Promotion $promoName.")
            val start = now.minusMonths(1).toStartOfMonth().toDate()
            val end = now.toStartOfMonth().toDate()
            processGames(from = start, to = end)

            // End the session
            with(promoName) {
                printSessionStats(this)
                printFinalRanking(this)
                promoteHunters(this)
                restartExam(this)
            }
        } else {
            val start = now.toStartOfMonth().toDate()
            val end = now.toDate()
            processGames(from = start, to = end)
        }

        if (now.hour in 7..9) printDailyRanking()
    }

    private fun processGames(from: Date, to: Date) {
        log(INFO, "Processing exam games from $from to $to")

        DatabaseAccessor.examGames(from, to)
            .forEach { game ->
                log(INFO, "Treating game ${game.id}")
                addExamPoints(game, game.blackPlayerDiscordId, true)
                addExamPoints(game, game.whitePlayerDiscordId, false)
            }
    }

    private fun addExamPoints(
        game: Game,
        mainPlayerDiscordId: String?,
        isBlack: Boolean
    ) {
        DatabaseAccessor.addExamPoints(mainPlayerDiscordId, ExamPoints.fromGame(game, isBlack))
    }

    private fun printDailyRanking() {
        log(INFO, "printDailyRanking")

        var message = ""
        DatabaseAccessor.examPlayers()
            .filter { it.totalPoints() > 0 }
            .sortedWith(ExamPlayerComparator())
            .take(20)
            .forEachIndexed { index, hunter ->
                message += "\n**${index + 1}.** ${jda.userName(hunter)} *(${hunter.totalPointsString()})*${hunter.title()}"
            }

        val title = "$EMOJI __Classement de l'**Examen Hunter**__ $EMOJI"
        if (message.isBlank()) message = "*Aucun participant n'a de points*"
        else message += "\n\n[Classement complet](${Config.FRONTEND_URL}/hunters)"

        jda.publicMessage(Config.Exam.CHANNEL_ID, "$title\n$message")
    }

    private fun printSessionStats(promoName: String) {
        log(INFO, "printSessionStats")

        val stats = DatabaseAccessor.examStats()

        var message = "La session $promoName de l'**Examen Hunter** touche à sa fin !\n"
        message += "Les **${stats.candidates} candidats** au titre de **Hunter** ont cumulé **${stats.promoTotal} points** "
        message += "en jouant **${stats.gamesPlayed()} parties**, dont **${stats.internalGamesPlayed()}** entre membres de la promotion."

        // Netero Award, save score and check if it is best of all time
        message += "\n\n__Netero Award__\n\n"
        DatabaseAccessor.savePromotionScore(promoName, stats.promoTotal)
        val award = DatabaseAccessor.examAward()
        message += if (award?.score == stats.promoTotal)
            "En établissant le nouveau record de points jamais attribué en une session d'examen, la promotion $promoName se voit attribuer le **Netero Award** ! Bravo à tous les participants !"
        else
            "La promotion $promoName n'arrive pas à détrôner celle de ${award?.promo ?: "????"}, qui conserve donc le **Netero Award** pour un mois supplémentaire !"

        jda.publicMessage(
            Config.Exam.HOF_CHANNEL_ID,
            message,
            "$EMOJI __**Examen Hunter** ${promoName}__ $EMOJI"
        )
    }

    private fun printFinalRanking(promoName: String) {
        log(INFO, "printFinalRanking")

        val title =
            "$EMOJI __Classement final de l'**Examen Hunter** ${promoName}__ $EMOJI"

        // Print all in normal chanel
        jda.publicMessage(Config.Exam.CHANNEL_ID, title)

        var message = ""
        DatabaseAccessor.examPlayers()
            .filter { it.totalPoints() > 0 }
            .sortedWith(ExamPlayerComparator())
            .forEachIndexed { index, hunter ->
                message += "**${index + 1}.** ${jda.userName(hunter)} *(${hunter.totalPointsString()})*${hunter.title()}\n"
                if (index % 10 == 9) {
                    jda.publicMessage(Config.Exam.CHANNEL_ID, message)
                    message = ""
                }
            }

        if (message.isNotBlank()) jda.publicMessage(Config.Exam.CHANNEL_ID, message)

        // Print top 20 in HOF channel
        var hofMessage = ""
        DatabaseAccessor.examPlayers()
            .filter { it.totalPoints() > 0 }
            .sortedWith(ExamPlayerComparator())
            .take(20)
            .forEachIndexed { index, hunter ->
                hofMessage += "\n**${index + 1}.** ${jda.userName(hunter)} *(${hunter.totalPointsString()})*${hunter.title()}"
            }

        if (hofMessage.isBlank()) hofMessage = "*Aucun participant n'a de points*"
        jda.publicMessage(Config.Exam.HOF_CHANNEL_ID, "$title\n$hofMessage")
    }

    private fun promoteHunters(promoName: String) {
        log(INFO, "promoteHunters")

        val newHunters = DatabaseAccessor.examPlayers()
            .asSequence()
            .filter { it.totalPoints() > 0 }
            .sortedWith(ExamPlayerComparator())
            .take(10)
            .filter { !it.hunter }
            .toMutableList()

        newHunters.forEach { hunter ->
            DatabaseAccessor.promoteHunter(hunter)
            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "${jda.userName(hunter)} valide son examen et devient **Hunter** !"
            )
        }

        val specMessages = mutableListOf<String>()
        ExamSpecialization.values().forEach {
            val triple: Triple<ExamPlayer?, Boolean, String> = awardSpecialization(it)
            if (triple.first != null) {
                if (triple.second.not()) newHunters.add(triple.first as ExamPlayer)
                specMessages.add(triple.third)
            }
        }

        var hunterMessage = ""
        newHunters.forEach { hunterMessage += "${jda.userName(it)}\n" }
        jda.publicMessage(
            Config.Exam.HOF_CHANNEL_ID,
            hunterMessage,
            "$EMOJI __**Hunters** de la promotion ${promoName}__ $EMOJI"
        )

        var specMessage = ""
        specMessages.forEach { specMessage += "$it\n" }
        jda.publicMessage(
            Config.Exam.HOF_CHANNEL_ID,
            specMessage,
            "$EMOJI __Spécialisations **Hunter** attribuées__ $EMOJI"
        )
    }

    private fun awardSpecialization(specialization: ExamSpecialization): Triple<ExamPlayer?, Boolean, String> {
        log(INFO, "awardSpecialization")

        val hunters = DatabaseAccessor.examPlayers()
            .sortedBy { specialization.pointsCallback(it) }
            .reversed()
            .take(2)
            .filter {
                if (specialization == ExamSpecialization.HEAD) it.participation >= 5 // At least 5 games played
                else specialization.pointsCallback(it) >= 5 // At least 5 points earned
            }

        // Nobody is eligible
        if (hunters.isEmpty()) {
            DatabaseAccessor.resetSpec(specialization)

            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "\nPersonne n'est éligible pour devenir **${specialization.fullName}** ce mois ci !"
            )

            return Triple(null, false, "")
        }

        val hunter = hunters[0]

        // Equality
        if (hunters.size > 1 && specialization.pointsCallback(hunter) == specialization.pointsCallback(hunters[1])) {
            DatabaseAccessor.resetSpec(specialization)
            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "\nPlusieurs joueurs sont à égalité pour le titre de **${specialization.fullName}**, il n'est donc pas attribué ce mois ci !"
            )
            return Triple(null, false, "")
        }

        val wasHunter: Boolean = if (!hunter.hunter) {
            // First is not hunter => make hunter
            DatabaseAccessor.promoteHunter(hunter)
            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "\n${jda.userName(hunter)} est promu **Hunter** grâce à son haut-fait de **${specialization.type}** !"
            )
            false
        } else true

        // If new title, remove title for everyone
        if (specialization.titleCountCallback(hunter) == 0) {
            DatabaseAccessor.resetSpec(specialization)
        }

        // Increment spec
        DatabaseAccessor.incrementSpec(hunter, specialization)

        // Compute stars (use title count before increment to get the good number of stars)
        val starCount = min(3, specialization.titleCountCallback(hunter))
        var stars = ""
        for (i in 0 until starCount) stars += ":star:"

        // Display new title in public
        val name = jda.userName(hunter)
        val title = "${specialization.fullName}$stars"
        val message = "${specialization.emoji} $name obtient le titre de **$title** avec ${
            specialization.pointsStringCallback(hunter)
        } de **${specialization.type}**!"
        jda.publicMessage(Config.Exam.CHANNEL_ID, message)
        return Triple(hunter, wasHunter, message)
    }

    private fun restartExam(promoName: String) {
        log(INFO, "restartExam")

        DatabaseAccessor.clearExamPoints()

        var message = "$EMOJI __Clôture de l'**Examen Hunter**__ $EMOJI\n"
        message += "\nL'**Examen Hunter** $promoName est désormais clos. Une nouvelle session commence.\nQue brûlent vos nens ! :fire:"
        jda.publicMessage(Config.Exam.CHANNEL_ID, message)
    }
}
