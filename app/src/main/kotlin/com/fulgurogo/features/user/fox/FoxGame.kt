package com.fulgurogo.features.user.fox

import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.rankToString
import java.util.*

data class FoxGame(
    val chessid: Long = 0,
    val blacknick: String = "",
    val blackenname: String = "",
    val blackdan: Int = 0,
    val whitenick: String = "",
    val whiteenname: String = "",
    val whitedan: Int = 0,
    val title: String = "",
    val gamestarttime: Long = 0,
    val gameendtime: Long = 0,
    val winner: Int = 0,
    val point: Int = 0,
    val reason: Int = 0,
    val boardsize: Int = 0,
    val handicap: Int = 0,
    val komi: Int = 0,
    val starttime: String = "",
    val endtime: String = "",
    val gametype: Int = 0
) : UserAccountGame() {
    companion object {
        private const val BLACK_WIN = 1
        private const val WHITE_WIN = 2
        private const val POINT_VICTORY = 1
        private const val TIME_VICTORY = 2
        private const val RESIGN_VICTORY = 3
    }

    override fun date(): Date = Date(gameendtime * 1000)
    override fun server(): String = UserAccount.FOX.fullName
    override fun account(): UserAccount = UserAccount.FOX
    override fun serverId(): String = chessid.toString()

    override fun blackPlayerServerId(): String = blacknick
    override fun blackPlayerPseudo(): String = blackenname
    override fun blackPlayerRank(): String = blackdan.rankToString()
    override fun blackPlayerWon(): Boolean = winner == BLACK_WIN

    override fun whitePlayerServerId(): String = whitenick
    override fun whitePlayerPseudo(): String = whiteenname
    override fun whitePlayerRank(): String = whitedan.rankToString()
    override fun whitePlayerWon(): Boolean = winner == WHITE_WIN

    override fun isFinished(): Boolean = true
    override fun handicap(): Int = handicap
    override fun komi(): Double = (komi.toDouble() / 50)
    override fun isLongGame(): Boolean = false

    fun isNineteen(): Boolean = boardsize == 19
}
