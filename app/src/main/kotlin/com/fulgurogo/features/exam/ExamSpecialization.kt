package com.fulgurogo.features.exam

enum class ExamSpecialization(
    val fullName: String,
    val type: String,
    val emoji: String,
    val databaseId: String,
    val pointsCallback: (ExamPlayer) -> Double,
    val pointsStringCallback: (ExamPlayer) -> String,
    val titleCountCallback: (ExamPlayer) -> Int
) {
    INFORMATION(
        "Information Hunter",
        "Participation",
        ":detective:",
        "information",
        { it.participation.toDouble() },
        { "${it.participation} ${if (it.participation < 2) "pt" else "pts"}" },
        { it.information }
    ),
    LOST(
        "Lost Hunter",
        "Communauté",
        ":mag:",
        "lost",
        { it.community.toDouble() },
        { "${it.community} ${if (it.community < 2) "pt" else "pts"}" },
        { it.lost }
    ),
    RUIN(
        "Ruin Hunter",
        "Patience",
        ":moyai:",
        "ruin",
        { it.patience.toDouble() },
        { "${it.patience} ${if (it.patience < 2) "pt" else "pts"}" },
        { it.ruin }
    ),
    TREASURE(
        "Treasure Hunter",
        "Victoire",
        ":moneybag:",
        "treasure",
        { it.victory.toDouble() },
        { "${it.victory} ${if (it.victory < 2) "pt" else "pts"}" },
        { it.treasure }
    ),
    GOURMET(
        "Gourmet Hunter",
        "Raffinement",
        ":fork_knife_plate:",
        "gourmet",
        { it.refinement.toDouble() },
        { "${it.refinement} ${if (it.refinement < 2) "pt" else "pts"}" },
        { it.gourmet }
    ),
    BEAST(
        "Beast Hunter",
        "Performance",
        ":t_rex:",
        "beast",
        { it.performance.toDouble() },
        { "${it.performance} ${if (it.performance < 2) "pt" else "pts"}" },
        { it.beast }
    ),
    BLACKLIST(
        "Blacklist Hunter",
        "Prouesse",
        ":skull_crossbones:",
        "blacklist",
        { it.achievement.toDouble() },
        { "${it.achievement} ${if (it.achievement < 2) "pt" else "pts"}" },
        { it.blacklist }
    ),
    HEAD(
        "Head Hunter",
        "Ratio",
        ":dart:",
        "head",
        { it.pointsRatio() },
        { "${it.pointsRatio()} pts/partie" },
        { it.head }
    )
}
