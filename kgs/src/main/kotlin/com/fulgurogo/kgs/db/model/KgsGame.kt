package com.fulgurogo.kgs.db.model

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class KgsGame(
    val goldId: String,
    val date: Date,
    val blackId: String,
    val blackRank: String,
    val whiteId: String,
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
        val blackDesc = "**$blackId ($blackRank)**"
        val whiteDesc = "**$whiteId ($whiteRank)**"
        val gameLink = "${Config.get("frontend.url")}/game/$goldId"
        val gameDesc = if (isFinished()) "\n**[Voir la partie]($gameLink)**" else ""

        return when (result) {
            "black" -> "Victoire de $blackDesc contre $whiteDesc$gameDesc"
            "white" -> "Victoire de $whiteDesc contre $blackDesc$gameDesc"
            "jigo" -> "$blackDesc et $whiteDesc font match nul$gameDesc"
            else -> "$blackDesc :crossed_swords: $whiteDesc$gameDesc"
        }
    }
}
