package com.fulgurogo.features.api

import com.fulgurogo.features.exam.ExamPlayer
import com.fulgurogo.utilities.NoArg

@NoArg
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
        fun from(examPlayer: ExamPlayer?, apiPlayer: ApiPlayer): ApiExamPlayer = ApiExamPlayer(
            apiPlayer.discordId,
            apiPlayer.name ?: "",
            apiPlayer.avatar ?: "",
            examPlayer?.totalPoints() ?: 0,
            examPlayer?.participation ?: 0,
            examPlayer?.community ?: 0,
            examPlayer?.patience ?: 0,
            examPlayer?.victory ?: 0,
            examPlayer?.refinement ?: 0,
            examPlayer?.performance ?: 0,
            examPlayer?.achievement ?: 0,
            examPlayer?.hunter ?: false,
            examPlayer?.information ?: 0,
            examPlayer?.lost ?: 0,
            examPlayer?.ruin ?: 0,
            examPlayer?.treasure ?: 0,
            examPlayer?.gourmet ?: 0,
            examPlayer?.beast ?: 0,
            examPlayer?.blacklist ?: 0,
            examPlayer?.head ?: 0,
            examPlayer?.pointsRatio() ?: 0.toDouble()
        )
    }
}
