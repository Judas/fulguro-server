package com.fulgurogo.ogs.api.model

import com.google.gson.annotations.SerializedName
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

data class OgsApiGame(
    val id: Int = 0,
    val players: OgsApiGamePlayers,
    val started: String = "",
    val ended: String?,
    val annulled: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val handicap: Int = 0,
    val komi: String = "",
    val rengo: Boolean = false,
    val outcome: String = "",
    @SerializedName("black_lost") val blackLost: Boolean,
    @SerializedName("white_lost") val whiteLost: Boolean,
    @SerializedName("time_control_parameters") val timeControlParams: String = "",
    @SerializedName("time_per_move") val timePerMove: Int? = null
) {
    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX"
        private const val DATE_FORMAT_OLD = "yyyy-MM-dd'T'HH:mm:ssX"
    }

    fun goldId(): String = "OGS_$id"

    fun date(): Date = try {
        SimpleDateFormat(DATE_FORMAT).parse(started, ParsePosition(0))
    } catch (_: Exception) {
        SimpleDateFormat(DATE_FORMAT_OLD).parse(started, ParsePosition(0))
    }

    fun result(): String? = when {
        outcome.isBlank() -> "unfinished"
        outcome == "0 points" -> "jigo"
        whiteLost -> "black"
        blackLost -> "white"
        else -> null
    }

    fun isLongGame(): Boolean {
        val speed = extractTimeControlParamString("speed")
        val system = extractTimeControlParamString("system")
        return if (speed.isNotBlank() && speed != "live") false
        else when (system) {
            "byoyomi", "canadian" -> extractTimeControlParamInt("main_time") >= 1200
            "absolute" -> extractTimeControlParamInt("total_time") >= 2400
            "simple" -> extractTimeControlParamInt("per_move") >= 30
            "fischer" -> extractTimeControlParamInt("initial_time") >= 600
                    && extractTimeControlParamInt("time_increment") >= 20

            else -> false
        }
    }

    fun isCorrespondence(): Boolean {
        val speed = extractTimeControlParamString("speed")
        return when {
            speed.isNotBlank() -> speed == "correspondence"
            timePerMove != null -> timePerMove >= 14400 // Correspondence increment starts at 4h
            else -> true // we don't know, consider it correspondence (will be filtered)
        }
    }

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
    } catch (_: NumberFormatException) {
        0
    }

    private fun extractTimeControlParamString(key: String): String = extractTimeControlParam(key).filter { it != '"' }
}
