package com.fulgurogo.features.exam

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ExamSessionStats(
    val totalParticipation: Int = 0,
    val totalCommunity: Int = 0,
    val candidates: Int = 0,
    val promoTotal: Int = 0
) {
    fun gamesPlayed(): Int = totalParticipation - internalGamesPlayed()
    fun internalGamesPlayed(): Int = totalCommunity / 4
}
