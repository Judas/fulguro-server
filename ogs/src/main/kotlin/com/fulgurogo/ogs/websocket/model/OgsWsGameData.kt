package com.fulgurogo.ogs.websocket.model

import com.google.gson.annotations.SerializedName
import java.util.*

data class OgsWsGameData(
    @SerializedName("game_id") val id: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val handicap: Int = 0,
    val ranked: Boolean = false,
    val komi: String = "",
    val rengo: Boolean = false,
    val players: OgsWsGamePlayers,
    @SerializedName("time_control") val timeControl: OgsWsGameTimeControl,
    val phase: String = "",
    @SerializedName("start_time") val start: Long = 0,
    val winner: Int = 0,
    val outcome: String? = ""
) {
    fun goldId(): String = "OGS_$id"
    fun isFinished() = !outcome.isNullOrBlank()
    fun date() = Date(start)

    fun result(): String? = when {
        outcome.isNullOrBlank() -> "unfinished"
        outcome == "0 points" -> "jigo"
        winner == players.black.id -> "black"
        winner == players.white.id -> "white"
        else -> null
    }

    fun isLongGame() = timeControl.isLongGame()
}

data class OgsWsGamePlayers(
    val black: OgsWsUser,
    val white: OgsWsUser
)

data class OgsWsUser(
    val id: Int = 0,
    val rank: Double = 0.0,
    val username: String = "",
)

data class OgsWsGameTimeControl(
    @SerializedName("system") val system: String = "",
    @SerializedName("main_time") val mainTime: Int = 0,
    @SerializedName("total_time") val totalTime: Int = 0,
    @SerializedName("per_move") val perMove: Int = 0,
    @SerializedName("initial_time") val initialTime: Int = 0,
    @SerializedName("time_increment") val timeIncrement: Int = 0
) {
    fun isLongGame(): Boolean = when (system) {
        "byoyomi", "canadian" -> mainTime >= 1200
        "absolute" -> totalTime >= 2400
        "simple" -> perMove >= 30
        "fischer" -> initialTime >= 600 && timeIncrement >= 20
        else -> false
    }
}
