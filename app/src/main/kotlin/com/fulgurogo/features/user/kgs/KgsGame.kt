package com.fulgurogo.features.user.kgs

import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountGame
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

    var isShortGame: Boolean? = null

    override fun date(): Date = SimpleDateFormat(DATE_FORMAT).parse(timestamp, ParsePosition(0))
    override fun server(): String = UserAccount.KGS.fullName
    override fun account(): UserAccount = UserAccount.KGS
    override fun serverId(): String = timestamp

    override fun blackPlayerServerId(): String = players.black.name
    override fun blackPlayerPseudo(): String = players.black.name
    override fun blackPlayerRank(): String = players.black.rank
    override fun blackPlayerWon(): Boolean = score.toDoubleOrNull()?.let { it > 0 } ?: run { score.startsWith("B+") }

    override fun whitePlayerServerId(): String = players.white.name
    override fun whitePlayerPseudo(): String = players.white.name
    override fun whitePlayerRank(): String = players.black.rank
    override fun whitePlayerWon(): Boolean = score.toDoubleOrNull()?.let { it < 0 } ?: run { score.startsWith("W+") }

    override fun isFinished(): Boolean = score.isNotBlank() && score != "UNFINISHED"
    override fun handicap(): Int = handicap
    override fun komi(): Double = komi
    override fun isLongGame(): Boolean = isShortGame == false
    fun isRanked(): Boolean = RANKED == gameType
    fun isFree(): Boolean = FREE == gameType
    fun isNineteen(): Boolean = size == 19
}
