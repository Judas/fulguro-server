package com.fulgurogo.features.exam

import com.fulgurogo.utilities.formatDecimals

data class ExamPlayer(
    val discordId: String,

    val participation: Int = 0,
    val community: Int = 0,
    val patience: Int = 0,
    val victory: Int = 0,
    val refinement: Int = 0,
    val performance: Int = 0,
    val achievement: Int = 0,

    val hunter: Boolean,

    val information: Int = 0,
    val lost: Int = 0,
    val ruin: Int = 0,
    val treasure: Int = 0,
    val gourmet: Int = 0,
    val beast: Int = 0,
    val blacklist: Int = 0,
    val head: Int = 0
) {
    fun totalPoints(): Int =
        participation + community + patience + victory + refinement + performance + achievement

    fun totalPointsString(): String = totalPoints().let { "$it ${if (it < 2) "pt" else "pts"}" }

    fun pointsRatio(): Double = if (participation < 5) 0.toDouble()
    else (totalPoints().toDouble() / participation.toDouble()).formatDecimals()

    fun title(): String {
        var title = ""
        if (hunter) title += " :military_medal:"
        ExamSpecialization.values().forEach { spec ->
            val starCount = spec.titleCountCallback(this)
            if (starCount > 0) { // starCount = 2 means 1 star
                var stars = ""
                for (i in 1 until starCount) stars += ":star:"
                title += " - ${spec.emoji}$stars"
            }
        }
        return title
    }
}
