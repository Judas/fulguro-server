package com.fulgurogo.features.exam

data class ExamAward(
    val id: Int,
    val promo: String = "",
    val score: Int = 0,
    val players: Int = 0,
    val games: Int = 0,
    val communityGames: Int = 0
)
