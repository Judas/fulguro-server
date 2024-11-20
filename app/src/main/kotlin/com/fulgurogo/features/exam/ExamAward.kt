package com.fulgurogo.features.exam

import com.fulgurogo.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ExamAward(
    val id: Int,
    val promo: String = "",
    val score: Int = 0,
    val players: Int = 0,
    val games: Int = 0,
    val communityGames: Int = 0
)
