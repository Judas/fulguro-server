package com.fulgurogo.features.user.ogs

import com.google.gson.annotations.SerializedName
import java.util.*

data class OgsGameData(
    @SerializedName("time_control") val timeControl: OgsTimeControl? = null
)


data class OgsTimeControl(
    val speed: String,
    val system: String,

    // BYOYOMI / CANADIAN
    @SerializedName("main_time") val mainTimeSeconds: Int = 0,

    // FISCHER
    @SerializedName("initial_time") val initialTimeSeconds: Int = 0,
    @SerializedName("time_increment") val timeIncrementSeconds: Int = 0,

    // SIMPLE
    @SerializedName("per_move") val perMoveSeconds: Int = 0,

    // ABSOLUTE
    @SerializedName("total_time") val totalTimeSeconds: Int = 0
) {
    fun isCorrespondenceGame(): Boolean = speed == "correspondence"

    fun isLongGame(): Boolean =
        if (speed != "live") false
        else when (system) {
            "byoyomi", "canadian" -> mainTimeSeconds >= 1200
            "fischer" -> initialTimeSeconds >= 600 && timeIncrementSeconds >= 20
            "absolute" -> totalTimeSeconds >= 2400
            "simple" -> perMoveSeconds >= 30
            else -> false
        }
}
