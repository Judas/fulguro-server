package com.fulgurogo.features.user.ogs

import com.fulgurogo.Config
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
    @SerializedName("time_control_parameters") val timeControlParams: String = ""
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

    fun endDate(): Date? = if (ended.isBlank()) null else try {
        SimpleDateFormat(DATE_FORMAT).parse(ended, ParsePosition(0))
    } catch (e: Exception) {
        SimpleDateFormat(DATE_FORMAT_OLD).parse(ended, ParsePosition(0))
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

    override fun isLongGame(): Boolean {
        val speed = extractTimeControlParamString("speed")
        return if (speed.isNotBlank()) speed == "live"
        else when (extractTimeControlParamString("system")) {
            "byoyomi", "canadian" -> extractTimeControlParamInt("main_time") >= 1200
            "absolute" -> extractTimeControlParamInt("total_time") >= 2400
            "simple" -> extractTimeControlParamInt("per_move") >= 30
            "fischer" -> extractTimeControlParamInt("initial_time") >= 600
                    && extractTimeControlParamInt("time_increment") >= 20

            else -> false
        }
    }

    override fun sgfLink(blackPlayerDiscordId: String, whitePlayerDiscordId: String) =
        "${Config.Ogs.API_URL}/games/$id/sgf"

    fun isNotCorrespondence(): Boolean {
        val speed = extractTimeControlParamString("speed")
        return speed.isNotBlank() && speed != "correspondence"
    }

    private fun isDraw() = outcome == "0 points"
    fun isNotCancelled(): Boolean = !cancelled
    fun isNineteen(): Boolean = width == 19 && height == 19
    fun isNotBotGame(): Boolean = !historicalRatings.black.isBot() && !historicalRatings.white.isBot()
    fun isRengo(): Boolean = rengo

    private fun extractTimeControlParam(key: String): String {
        val keyString = "\"$key\": "
        if (!timeControlParams.contains(keyString)) return ""

        var value: String = timeControlParams.substring(timeControlParams.indexOf(keyString))
        value = value.substring(keyString.length)

        var end = value.indexOf(",")
        if (end == -1) end = value.indexOf("}")
        if (end == -1) end = value.length
        value = value.substring(0, end)

        return value
    }

    private fun extractTimeControlParamInt(key: String): Int = try {
        extractTimeControlParam(key).toInt()
    } catch (e: NumberFormatException) {
        0
    }

    private fun extractTimeControlParamString(key: String): String = extractTimeControlParam(key).filter { it != '"' }
}
