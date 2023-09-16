package com.fulgurogo.features.api

import com.fulgurogo.features.exam.ExamSpecialization
import com.fulgurogo.features.exam.NamedExamPlayer

data class ApiExamTitle(
    val discordId: String?,
    val name: String?,
    val title: String,
    val emoji: String,
    val stars: Int
) {
    companion object {
        fun from(player: NamedExamPlayer?, spec: ExamSpecialization) = ApiExamTitle(
            player?.discordId,
            player?.name ?: "",
            spec.fullName,
            spec.unicodeEmoji,
            player?.let { spec.titleCountCallback(it) - 1 } ?: 0
        )
    }
}
