package com.fulgurogo.kgs.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class KgsGame(
    val date: Date,
    val blackName: String,
    val blackRank: String,
    val whiteName: String,
    val whiteRank: String,
    val size: Int,
    val komi: Float,
    val handicap: Int,
    val longGame: Boolean,
    val result: String, // Black / White / Jigo / Unfinished
    val sgf: String
) {
    fun isFinished(): Boolean = result != "UNFINISHED"
    fun description(): String {
        val blackDesc = "$blackName ($blackRank)"
        val whiteDesc = "$whiteName ($whiteRank)"
        return when (result) {
            "BLACK" -> "$blackDesc gagne contre $whiteDesc"
            "WHITE" -> "$whiteDesc gagne contre $blackDesc"
            "JIGO" -> "$blackDesc et $whiteDesc font match nul (jigo)"
            else -> "$blackDesc :crossed_swords: $whiteDesc"
        }
    }
}
