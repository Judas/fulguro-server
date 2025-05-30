package com.fulgurogo.kgs.db.model

import java.util.*

data class KgsGame(
    val date: Date,
    val blackName: String,
    val blackRank: String,
    val blackWon: Boolean,
    val whiteName: String,
    val whiteRank: String,
    val whiteWon: Boolean,
    val komi: Float,
    val handicap: Int,
    val longGame: Boolean,
    val sgf: String
)
