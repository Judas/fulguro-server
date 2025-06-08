package com.fulgurogo.ogs.db.model

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class OgsGame(
    val goldId: String,
    val id: Int,
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
    val longGame: Boolean,
    val ranked: Boolean,
    val result: String, // black / white / jigo / unfinished
    val sgf: String
) {
    fun isFinished(): Boolean = result != "unfinished"
    fun description(): String {
        val blackDesc = "**$blackName ($blackRank)**"
        val whiteDesc = "**$whiteName ($whiteRank)**"
        val gameLink =
            if (isFinished()) "${Config.get("frontend.url")}/game/$goldId"
            else "${Config.get("ogs.website.url")}/game/$id"
        val gameDesc = "\n**[Voir la partie]($gameLink)**"

        return when (result) {
            "black" -> "Victoire de $blackDesc contre $whiteDesc$gameDesc"
            "white" -> "Victoire de $whiteDesc contre $blackDesc$gameDesc"
            "jigo" -> "$blackDesc et $whiteDesc font match nul$gameDesc"
            else -> "$blackDesc :crossed_swords: $whiteDesc$gameDesc"
        }
    }
}
