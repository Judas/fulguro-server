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

    var mainPlayerId = 0

    override fun date(): Date = try {
        SimpleDateFormat(DATE_FORMAT).parse(started, ParsePosition(0))
    } catch (e: Exception) {
        SimpleDateFormat(DATE_FORMAT_OLD).parse(started, ParsePosition(0))
    }

    override fun server(): String = UserAccount.OGS.fullName
    override fun account(): UserAccount = UserAccount.OGS
    override fun gameServerId(): String = id.toString()

    private fun mainPlayer(): OgsUser = historicalRatings.let {
        if (it.black.id == mainPlayerId) it.black
        else it.white
    }

    override fun mainPlayerAccountId(): String = mainPlayerId.toString()
    override fun mainPlayerRank(): String =
        with(mainPlayer()) { return rankString() + if (hasStableRank()) "" else "?" }

    private fun opponent(): OgsUser = historicalRatings.let {
        if (it.black.id == mainPlayerId) it.white
        else it.black
    }

    override fun opponentAccountId(): String = opponent().id.toString()
    override fun opponentRank(): String = with(opponent()) { return rankString() + if (hasStableRank()) "" else "?" }

    override fun opponentPseudo(): String = opponent().username ?: ""

    override fun isFinished(): Boolean = outcome.isNotBlank()
    override fun isBlack(): Boolean = historicalRatings.black.id == mainPlayerId
    override fun isWin(): Boolean = when {
        isDraw() -> false
        historicalRatings.black.id == mainPlayerId -> whiteLost
        else -> blackLost
    }

    override fun isLoss(): Boolean = when {
        isDraw() -> false
        historicalRatings.black.id == mainPlayerId -> blackLost
        else -> whiteLost
    }

    private fun isDraw() = outcome == "0 points"
    override fun handicap(): Int = handicap
    override fun komi(): Double = komi.toDouble()
    override fun isLongGame(): Boolean = isLiveGame() && when (timeControl) {
        "byoyomi" -> extractTime("main_time") >= 1200
        "canadian" -> extractTime("main_time") >= 1200
        "fischer" -> extractTime("initial_time") >= 600 && extractTime("time_increment") >= 20
        "absolute" -> false // No absolute game authorized
        else -> false
    }

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
