package com.fulgurogo.fox.api.model

data class FoxApiResult(
    val winner: String? = "", // NONE / BLACK / WHITE
    val reason: String? = "", // COUNT / TIMEOUT / RESIGN / DRAW_AGREED / COUNT_ERR / FORCE_COUNT / FORFEIT / ADMIN / AI_REFEREE / UNKNOWN
    val score: Double? = 0.0
) {
    fun result(): String = when (winner) {
        "BLACK" -> "black"
        "WHITE" -> "white"
        "NONE" -> if (reason == "DRAW_AGREED") "jigo" else "unfinished"
        else -> "unfinished"
    }
}
