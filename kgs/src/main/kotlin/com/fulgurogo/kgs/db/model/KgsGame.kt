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
    val result: String, // black / white / jigo / unfinished
    val sgf: String
) {
    fun isFinished(): Boolean = result != "unfinished"
    fun description(): String {
        val blackDesc = "$blackName ($blackRank)"
        val whiteDesc = "$whiteName ($whiteRank)"
        return when (result) {
            "black" -> "$blackDesc gagne contre $whiteDesc"
            "white" -> "$whiteDesc gagne contre $blackDesc"
            "jigo" -> "$blackDesc et $whiteDesc font match nul (jigo)"
            else -> "$blackDesc :crossed_swords: $whiteDesc"
        }
    }
}
