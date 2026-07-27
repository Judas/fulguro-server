package com.fulgurogo.ogs.api.model

import com.google.gson.annotations.SerializedName
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
    val ranked: Boolean = false,
    val komi: String = "",
    val rengo: Boolean = false,
    val outcome: String = "",
    @SerializedName("black_lost") val blackLost: Boolean,
    @SerializedName("white_lost") val whiteLost: Boolean,
    @SerializedName("time_control_parameters") val timeControlParams: String = "",
    @SerializedName("time_per_move") val timePerMove: Int? = null
) {
    fun goldId(): String = "OGS_$id"

    /**
     * OGS sends ISO-8601 with a microsecond fraction (`2026-07-27T10:00:00.123456Z`), and none at all on older
     * records. `ISO_OFFSET_DATE_TIME` reads both, and reads the fraction as a fraction.
     *
     * Null when [started] cannot be parsed, so one malformed record skips its own game instead of failing the tick.
     *
     * The previous `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX")` read the six fractional digits as *milliseconds*
     * — `.123456` became 123456ms, putting every game 2m03s late. And its `parse(String, ParsePosition)` overload
     * returns null instead of throwing, so the `catch` that fell back to the no-fraction format never ran; the null
     * surfaced later as an NPE in the caller's date comparison.
     */
    fun date(): Date? = try {
        Date.from(OffsetDateTime.parse(started, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant())
    } catch (_: DateTimeParseException) {
        null
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
