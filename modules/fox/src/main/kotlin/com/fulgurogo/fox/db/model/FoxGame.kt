package com.fulgurogo.fox.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.Date

@GenerateNoArgConstructor
data class FoxGame(
    val goldId: String,
    val chessId: String,
    val date: Date,
    val blackId: String,
    val blackName: String,
    val blackRank: String,
    val whiteId: String,
    val whiteName: String,
    val whiteRank: String,
    val size: Int,
    val komi: Double,
    val handicap: Int,
    val ranked: Boolean = false,
    val longGame: Boolean = false,
    val result: String,
    val sgf: String,
)
