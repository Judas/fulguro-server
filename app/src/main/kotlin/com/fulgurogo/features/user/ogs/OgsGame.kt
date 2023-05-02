package com.fulgurogo.features.user.ogs

import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountGame
import com.google.gson.annotations.SerializedName
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

data class OgsGame(
    val id: Long = 0,
    @SerializedName("historical_ratings") val historicalRatings: OgsGamePlayers,
    val ranked: Boolean = false,
    @SerializedName("black_lost") val blackLost: Boolean = false,
    @SerializedName("white_lost") val whiteLost: Boolean = false,
    val started: String = "",
    val ended: String = "",
    @SerializedName("annulled") val cancelled: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val handicap: Int = 0,
    val rules: String = "",
    val komi: String = "",
    val rengo: Boolean = false,
    @SerializedName("disable_analysis") val analysisDisabled: Boolean = false,
    val outcome: String = "",
    @SerializedName("gamedata") val gameData: OgsGameData? = null
) : UserAccountGame() {
    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX"
        private const val DATE_FORMAT_OLD = "yyyy-MM-dd'T'HH:mm:ssX"
    }

    override fun date(): Date = try {
        SimpleDateFormat(DATE_FORMAT).parse(started, ParsePosition(0))
    } catch (e: Exception) {
        SimpleDateFormat(DATE_FORMAT_OLD).parse(started, ParsePosition(0))
    }

    override fun server(): String = UserAccount.OGS.fullName
    override fun account(): UserAccount = UserAccount.OGS
    override fun serverId(): String = id.toString()

    override fun blackPlayerServerId(): String = historicalRatings.black.id.toString()
    override fun blackPlayerPseudo(): String = historicalRatings.black.username ?: ""
    override fun blackPlayerRank(): String = with(historicalRatings.black) {
        return rankString() + if (hasStableRank()) "" else "?"
    }

    override fun blackPlayerWon(): Boolean = !isDraw() && whiteLost

    override fun whitePlayerServerId(): String = historicalRatings.white.id.toString()
    override fun whitePlayerPseudo(): String = historicalRatings.white.username ?: ""
    override fun whitePlayerRank(): String = with(historicalRatings.white) {
        return rankString() + if (hasStableRank()) "" else "?"
    }

    override fun whitePlayerWon(): Boolean = !isDraw() && blackLost

    override fun isFinished(): Boolean = outcome.isNotBlank()
    override fun handicap(): Int = handicap
    override fun komi(): Double = komi.toDouble()
    override fun isLongGame(): Boolean = gameData?.timeControl?.isLongGame() ?: false

    private fun isDraw() = outcome == "0 points"
    fun isNotCancelled(): Boolean = !cancelled
    fun isCorrespondenceGame(): Boolean = gameData?.timeControl?.isCorrespondenceGame() ?: true
    fun isNineteen(): Boolean = width == 19 && height == 19
    fun isNotBotGame(): Boolean = !historicalRatings.black.isBot() && !historicalRatings.white.isBot()
    fun isRengo(): Boolean = rengo
}
