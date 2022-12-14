package com.fulgurogo.features.exam

import com.fulgurogo.Config
import com.fulgurogo.features.bot.Command
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanListener
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.INFO
import net.dv8tion.jda.api.JDA
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class ExamService(private val jda: JDA) : GameScanListener {
    override fun onScanStarted() {
        log(INFO, "onScanStarted")
        handleAllUsers { DatabaseAccessor.ensureExamPlayer(it) }
    }

    override fun onScanFinished() {
        log(INFO, "onScanFinished")

        // Clear all exam points
        DatabaseAccessor.clearExamPoints()

        val now = ZonedDateTime.now(DATE_ZONE)
        val promoName = DateTimeFormatter
            .ofPattern("MMMM YYYY", Locale.FRANCE)
            .withLocale(Locale.FRANCE)
            .format(now.minusMonths(1))
            .replaceFirstChar { it.titlecase() }

        val shouldCloseSession = !DatabaseAccessor.hasPromotionScore(promoName)
        if (shouldCloseSession) {
            log(INFO, "Promotion $promoName is not closed, closing it.")
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
        DatabaseAccessor.examGames(from, to)
            .forEach { game ->
                log(INFO, "Treating game ${game.id}")

                // Game standard points
                DatabaseAccessor.addExamGame(game.mainPlayerId, ExamPoints.fromGame(game))

                // Game phantom points
                game.opponentId?.let { opponentDiscordId ->
                    val opponentPhantom =
                        DatabaseAccessor.examPhantoms().firstOrNull { it.discordId == opponentDiscordId }
                    if (opponentPhantom != null && !opponentPhantom.revealed && game.mainPlayerWon == true) {
                        // Phantom revealed !
                        DatabaseAccessor.revealExamPhantom(game.mainPlayerId, game.opponentId)

                        val revealerName = jda.userName(game.mainPlayerId)
                        val phantomName = jda.userName(game.opponentId)
                        jda.publicMessage(
                            Config.Exam.CHANNEL_ID,
                            ":ghost: $revealerName a d??masqu?? un membre de la **Brigade Fant??me** : $phantomName :ghost:"
                        )
                    }
                }
            }
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

        val title = "${Command.Exam.EMOJI} __Classement de l'**Examen Hunter**__ ${Command.Exam.EMOJI}"
        if (message.isBlank()) message = "*Aucun participant n'a de points*"
        jda.publicMessage(Config.Exam.CHANNEL_ID, "$title\n$message")
    }

    private fun printSessionStats(promoName: String) {
        log(INFO, "printSessionStats")

        val stats = DatabaseAccessor.examStats()

        var message = "La session $promoName de l'**Examen Hunter** touche ?? sa fin !\n"
        message += "Les **${stats.candidates} candidats** au titre de **Hunter** ont cumul?? **${stats.promoTotal} points** "
        message += "en jouant **${stats.gamesPlayed()} parties**, dont **${stats.internalGamesPlayed()}** entre membres de la promotion."

        // Netero Award, save score and check if it is best of all time
        message += "\n\n__Netero Award__\n\n"
        DatabaseAccessor.savePromotionScore(promoName, stats.promoTotal)
        val award = DatabaseAccessor.examAward()
        message += if (award.score == stats.promoTotal)
            "En ??tablissant le nouveau record de points jamais attribu?? en une session d'examen, la promotion $promoName se voit attribuer le **Netero Award** ! Bravo ?? tous les participants !"
        else
            "La promotion $promoName n'arrive pas ?? d??tr??ner celle de ${award.promo}, qui conserve donc le **Netero Award** pour un mois suppl??mentaire !"

        jda.publicMessage(
            Config.Exam.HOF_CHANNEL_ID,
            message,
            "${Command.Exam.EMOJI} __**Examen Hunter** ${promoName}__ ${Command.Exam.EMOJI}"
        )
    }

    private fun printFinalRanking(promoName: String) {
        log(INFO, "printFinalRanking")

        val title =
            "${Command.Exam.EMOJI} __Classement final de l'**Examen Hunter** ${promoName}__ ${Command.Exam.EMOJI}"

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

        // Phantom revealers become hunters if half of the phantoms are revealed
        val phantoms = DatabaseAccessor.examPhantoms()
        val revealedCount = phantoms.count { it.revealed }
        if (revealedCount * 2 > phantoms.size) {
            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                ":ghost: Bravo les Hunters, la moiti?? de la **Brigade Fant??me** a ??t?? d??masqu??e ! :ghost:"
            )

            phantoms
                .filter { it.revealed }
                .forEach { phantom ->
                    DatabaseAccessor.examPlayer(phantom.revealer)?.let { revealer ->
                        if (!revealer.hunter) {
                            newHunters.add(revealer)
                            DatabaseAccessor.promoteHunter(revealer)
                            jda.publicMessage(
                                Config.Exam.CHANNEL_ID,
                                "${jda.userName(revealer)} devient **Hunter** en ayant d??masqu?? un membre de la **Brigade Fant??me** !"
                            )
                        }
                    }
                }
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
            "${Command.Exam.EMOJI} __**Hunters** de la promotion ${promoName}__ ${Command.Exam.EMOJI}"
        )

        var specMessage = ""
        specMessages.forEach { specMessage += "$it\n" }
        jda.publicMessage(
            Config.Exam.HOF_CHANNEL_ID,
            specMessage,
            "${Command.Exam.EMOJI} __Sp??cialisations **Hunter** attribu??es__ ${Command.Exam.EMOJI}"
        )
    }

    private fun awardSpecialization(specialization: ExamSpecialization): Triple<ExamPlayer?, Boolean, String> {
        log(INFO, "awardSpecialization")

        val hunters = DatabaseAccessor.examPlayers()
            .sortedBy { specialization.pointsCallback(it) }
            .reversed()
            .take(2)
            .filter {
                if (specialization == ExamSpecialization.HEAD) it.participation > 5 // At least 5 games played
                else specialization.pointsCallback(it) > 5 // At least 5 points earned
            }

        // Nobody is eligible
        if (hunters.isEmpty()) {
            DatabaseAccessor.resetSpec(specialization)

            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "\nPersonne n'est ??ligible pour devenir **${specialization.fullName}** ce mois ci !"
            )

            return Triple(null, false, "")
        }

        val hunter = hunters[0]

        // Equality
        if (hunters.size > 1 && specialization.pointsCallback(hunter) == specialization.pointsCallback(hunters[1])) {
            DatabaseAccessor.resetSpec(specialization)
            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "\nPlusieurs joueurs sont ?? ??galit?? pour le titre de **${specialization.fullName}**, il n'est donc pas attribu?? ce mois ci !"
            )
            return Triple(null, false, "")
        }

        val wasHunter: Boolean = if (!hunter.hunter) {
            // First is not hunter => make hunter
            DatabaseAccessor.promoteHunter(hunter)
            jda.publicMessage(
                Config.Exam.CHANNEL_ID,
                "\n${jda.userName(hunter)} est promu **Hunter** gr??ce ?? son haut-fait de **${specialization.type}** !"
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
        val starCount = specialization.titleCountCallback(hunter)
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
        DatabaseAccessor.clearPhantomPoints()

        var message = "${Command.Exam.EMOJI} __Cl??ture de l'**Examen Hunter**__ ${Command.Exam.EMOJI}\n"
        message += "\nL'**Examen Hunter** $promoName est d??sormais clos. Une nouvelle session commence.\nQue br??lent vos nens ! :fire:"
        jda.publicMessage(Config.Exam.CHANNEL_ID, message)
    }
}
