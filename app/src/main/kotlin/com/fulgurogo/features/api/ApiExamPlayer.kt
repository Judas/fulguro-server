package com.fulgurogo.features.api

import com.fulgurogo.Config
import com.fulgurogo.features.exam.ExamPlayer
import com.fulgurogo.features.user.User

data class ApiExamPlayer(
    val discordId: String,
    val name: String,
    val avatar: String,

    val total: Int,
    val participation: Int,
    val community: Int,
    val patience: Int,
    val victory: Int,
    val refinement: Int,
    val performance: Int,
    val achievement: Int,

    val hunter: Boolean,

    val information: Int,
    val lost: Int,
    val ruin: Int,
    val treasure: Int,
    val gourmet: Int,
    val beast: Int,
    val blacklist: Int,
    val head: Int,
    val ratio: Double
) {

    companion object {
        fun from(player: ExamPlayer, user: User) = ApiExamPlayer(
            user.discordId,
            user.name ?: "Joueur inconnu",
            user.avatar ?: Config.Ladder.DEFAULT_AVATAR,
            player.totalPoints(),
            player.participation,
            player.community,
            player.patience,
            player.victory,
            player.refinement,
            player.performance,
            player.achievement,
            player.hunter,
            player.information,
            player.lost,
            player.ruin,
            player.treasure,
            player.gourmet,
            player.beast,
            player.blacklist,
            player.head,
            player.pointsRatio()
        )
    }
}
