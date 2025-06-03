package com.fulgurogo.ogs.db.model

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class OgsGame(
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
    val result: String, // black / white / jigo / unfinished
    val sgf: String
) {
    fun isFinished(): Boolean = result != "unfinished"
    fun description(): String {
        val blackDesc = "**$blackName ($blackRank)**"
        val whiteDesc = "**$whiteName ($whiteRank)**"
        val gameLink = "${Config.get("ogs.website.url")}/game/$id"
        return when (result) {
            "black" -> "$blackDesc __gagne__ contre $whiteDesc\n$gameLink"
            "white" -> "$whiteDesc __gagne__ contre $blackDesc\n$gameLink"
            "jigo" -> "$blackDesc et $whiteDesc font __match nul__ (jigo)\n$gameLink"
            else -> "$blackDesc :crossed_swords: $whiteDesc\n$gameLink"
        }
    }
}
