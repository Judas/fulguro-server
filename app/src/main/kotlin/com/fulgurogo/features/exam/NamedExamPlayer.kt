package com.fulgurogo.features.exam

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
class NamedExamPlayer(
    val name: String,
    discordId: String,
    participation: Int,
    community: Int,
    patience: Int,
    victory: Int,
    refinement: Int,
    performance: Int,
    achievement: Int,
    hunter: Boolean,
    information: Int,
    lost: Int,
    ruin: Int,
    treasure: Int,
    gourmet: Int,
    beast: Int,
    blacklist: Int,
    head: Int
) : ExamPlayer(
    discordId,
    participation,
    community,
    patience,
    victory,
    refinement,
    performance,
    achievement,
    hunter,
    information,
    lost,
    ruin,
    treasure,
    gourmet,
    beast,
    blacklist,
    head
)
