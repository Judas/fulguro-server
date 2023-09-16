package com.fulgurogo.features.api

import com.fulgurogo.Config
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.exam.ExamPlayer
import com.fulgurogo.features.exam.ExamSpecialization

data class ApiExamTitle(
    val discordId: String?,
    val name: String?,
    val avatar: String?,

    val title: String,
    val emoji: String,
    val stars: Int
) {
    companion object {
        fun from(player: ExamPlayer?, spec: ExamSpecialization): ApiExamTitle = player?.let {
            val user = DatabaseAccessor.ensureUser(it.discordId)
            ApiExamTitle(
                it.discordId,
                user.name,
                user.avatar,
                spec.fullName,
                spec.unicodeEmoji,
                spec.titleCountCallback(player) - 1
            )
        } ?: run {
            ApiExamTitle(
                null,
                "Titre vacant",
                Config.Ladder.DEFAULT_AVATAR,
                spec.fullName,
                spec.unicodeEmoji,
                0
            )
        }
    }
}
