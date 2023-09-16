package com.fulgurogo.features.api

import com.fulgurogo.features.exam.ExamSessionStats

data class ApiExamStats(
    val players: Int = 0,
    val games: Int = 0,
    val communityGames: Int = 0,
    val totalPoints: Int = 0
) {
    companion object {
        fun from(stats: ExamSessionStats) = ApiExamStats(
            stats.candidates,
            stats.gamesPlayed(),
            stats.internalGamesPlayed(),
            stats.totalParticipation
        )
    }
}
