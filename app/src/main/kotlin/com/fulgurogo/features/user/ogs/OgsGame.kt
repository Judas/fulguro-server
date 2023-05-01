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
    @SerializedName("time_per_move") val timePerMove: Int = 0,
    @SerializedName("time_control") val timeControl: String = "",
    @SerializedName("time_control_parameters") val timeControlParams: String = "",
    val handicap: Int = 0,
    val rules: String = "",
    val komi: String = "",
    val rengo: Boolean = false,
    @SerializedName("disable_analysis") val analysisDisabled: Boolean = false,
    val outcome: String = "",
) : UserAccountGame() {
    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX"
        private const val DATE_FORMAT_OLD = "yyyy-MM-dd'T'HH:mm:ssX"
        private const val BLITZ_TIME_PER_MOVE = 20
        private const val CORRESPONDENCE_TIME_PER_MOVE = 89280
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
    override fun isLongGame(): Boolean = isLiveGame() && when (timeControl) {
        "byoyomi", "canadian" -> extractTime("main_time") >= 1200
        "fischer" -> extractTime("initial_time") >= 600 && extractTime("time_increment") >= 20
        "absolute" -> extractTime("total_time") >= 2400
        else -> false
    }

    private fun isDraw() = outcome == "0 points"
    fun isNotCancelled(): Boolean = !cancelled
    fun isLiveGame(): Boolean =
        (timeControlParams.contains("\"speed\": \"live\"") || timePerMove in (BLITZ_TIME_PER_MOVE + 1) until CORRESPONDENCE_TIME_PER_MOVE)

    fun isCorrespondenceGame(): Boolean =
        timeControlParams.contains("\"speed\": \"correspondence\"") || timePerMove >= CORRESPONDENCE_TIME_PER_MOVE

    fun isNineteen(): Boolean = width == 19 && height == 19
    fun isNotBotGame(): Boolean = !historicalRatings.black.isBot() && !historicalRatings.white.isBot()
    fun isRengo(): Boolean = rengo

    private fun extractTime(key: String): Int {
        var time: String = timeControlParams.substring(timeControlParams.indexOf("\"$key\": "))
        time = time.substring(("\"$key\": ").length)

        var end = time.indexOf(",")
        if (end == -1) end = time.indexOf("}")
        if (end == -1) end = time.length
        time = time.substring(0, end)
        return try {
            time.toInt()
        } catch (e: NumberFormatException) {
            0
        }
    }
}
