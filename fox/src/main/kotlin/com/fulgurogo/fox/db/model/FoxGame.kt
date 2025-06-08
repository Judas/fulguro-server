package com.fulgurogo.fox.db.model

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class FoxGame(
    val goldId: String,
    val id: Long,
    val date: Date,
    val blackId: Int,
    val blackName: String,
    val blackRank: String,
    val whiteId: Int,
    val whiteName: String,
    val whiteRank: String,
    val size: Int,
    val komi: Double,
    val handicap: Int,
    val ranked: Boolean,
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
