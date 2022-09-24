package com.fulgurogo.features.user.kgs

import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.InvalidUserException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

data class KgsGame(
    val gameType: String = "", // demonstration, review, rengo_review, teaching, simul, rengo, free, ranked, tournament
    val score: String = "", // score (float) or UNKNOWN, UNFINISHED, NO_RESULT, B+RESIGN, W+RESIGN, B+FORFEIT, W+FORFEIT, B+TIME, or W+TIME.
    val komi: Double = 0.0,
    val size: Int = 0,
    val players: KgsGamePlayers,
    val timestamp: String = "",
    val handicap: Int = 0
) : UserAccountGame() {
    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX"
        private const val RANKED = "ranked"
        private const val FREE = "free"
    }

    var mainPlayer: KgsUser? = null
    var isShortGame: Boolean? = null

    private fun mainPlayerIsBlack(): Boolean = mainPlayer?.let { it.name.equals(players.black.name, true) }
        ?: throw InvalidUserException

    override fun date(): Date = SimpleDateFormat(DATE_FORMAT).parse(timestamp, ParsePosition(0))
    override fun server(): String = UserAccount.KGS.fullName
    override fun account(): UserAccount = UserAccount.KGS
    override fun gameServerId(): String = timestamp

    private fun mainPlayer(): KgsUser = if (mainPlayerIsBlack()) players.black else players.white
    override fun mainPlayerAccountId(): String = mainPlayer().name
    override fun mainPlayerRank(): String = mainPlayer().rank

    private fun opponent(): KgsUser = if (mainPlayerIsBlack()) players.white else players.black
    override fun opponentAccountId(): String = opponent().name
    override fun opponentRank(): String = opponent().rank
    override fun opponentPseudo(): String = opponent().name

    override fun isFinished(): Boolean = score.isNotBlank() && score != "UNFINISHED"
    private fun blackWon(): Boolean = score.toDoubleOrNull()?.let { it > 0 } ?: run { score.startsWith("B+") }
    private fun whiteWon(): Boolean = score.toDoubleOrNull()?.let { it < 0 } ?: run { score.startsWith("W+") }
    override fun isBlack(): Boolean = mainPlayerIsBlack()
    override fun isWin(): Boolean = if (mainPlayerIsBlack()) blackWon() else whiteWon()
    override fun isLoss(): Boolean = if (mainPlayerIsBlack()) whiteWon() else blackWon()
    override fun handicap(): Int = handicap
    override fun komi(): Double = komi
    override fun isLongGame(): Boolean = isShortGame == false
    fun isRanked(): Boolean = RANKED == gameType
    fun isFree(): Boolean = FREE == gameType
    fun isNineteen(): Boolean = size == 19
}
