package com.fulgurogo.features.user.fox

import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.rankToString
import java.util.*

data class FoxGame(
    val chessid: Long = 0,
    val blackuid: Long = 0,
    val blacknick: String = "",
    val blackenname: String = "",
    val blackdan: Int = 0,
    val blackcountry: Int = 86,
    val whiteuid: Long = 0,
    val whitenick: String = "",
    val whiteenname: String = "",
    val whitedan: Int = 0,
    val whitecountry: Int = 0,
    val title: String = "",
    val gamestarttime: Long = 0,
    val gameendtime: Long = 0,
    val winner: Int = 0,
    val point: Int = 0,
    val reason: Int = 0,
    val movenum: Int = 0,
    val boardsize: Int = 0,
    val handicap: Int = 0,
    val firstcolor: Int = 0,
    val komi: Int = 0,
    val clienttype: Int = 0,
    val commenttype: Int = 0,
    val additionalrule: Int = 0,
    val rule: Int = 0,
    val blackocc: Int = 0,
    val whiteocc: Int = 0,
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

    var mainPlayerPseudo = ""

    override fun date(): Date = Date(gameendtime * 1000)
    override fun server(): String = UserAccount.FOX.fullName
    override fun account(): UserAccount = UserAccount.FOX
    override fun gameServerId(): String = chessid.toString()
    override fun mainPlayerAccountId(): String = mainPlayerPseudo
    override fun mainPlayerRank(): String = if (isBlack()) blackdan.rankToString() else whitedan.rankToString()
    override fun opponentAccountId(): String = if (isBlack()) whitenick else blacknick
    override fun opponentRank(): String = if (isBlack()) whitedan.rankToString() else blackdan.rankToString()
    override fun opponentPseudo(): String = if (isBlack()) whiteenname else blackenname
    override fun isBlack(): Boolean = blacknick.equals(mainPlayerPseudo, true)
    override fun isWin(): Boolean = if (isBlack()) winner == BLACK_WIN else winner == WHITE_WIN
    override fun isLoss(): Boolean = !isWin()
    override fun handicap(): Int = handicap
    override fun komi(): Double = (komi.toDouble() / 50)
    override fun isLongGame(): Boolean = false
    override fun isFinished(): Boolean = true
}
