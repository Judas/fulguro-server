package com.fulgurogo.features.exam

enum class ExamSpecialization(
    val fullName: String,
    val type: String,
    val emoji: String,
    val unicodeEmoji: String,
    val databaseId: String,
    val pointsCallback: (ExamPlayer) -> Double,
    val pointsStringCallback: (ExamPlayer) -> String,
    val titleCountCallback: (ExamPlayer) -> Int
) {
    INFORMATION(
        "Information Hunter",
        "Participation",
        ":detective:",
        "\uD83D\uDD75\uFE0F\u200D",
        "information",
        { it.participation.toDouble() },
        { "${it.participation} ${if (it.participation < 2) "pt" else "pts"}" },
        { it.information }
    ),
    LOST(
        "Lost Hunter",
        "Communauté",
        ":mag:",
        "\uD83D\uDD0D",
        "lost",
        { it.community.toDouble() },
        { "${it.community} ${if (it.community < 2) "pt" else "pts"}" },
        { it.lost }
    ),
    RUIN(
        "Ruin Hunter",
        "Patience",
        ":moyai:",
        "\uD83D\uDDFF",
        "ruin",
        { it.patience.toDouble() },
        { "${it.patience} ${if (it.patience < 2) "pt" else "pts"}" },
        { it.ruin }
    ),
    TREASURE(
        "Treasure Hunter",
        "Victoire",
        ":moneybag:",
        "\uD83D\uDCB0",
        "treasure",
        { it.victory.toDouble() },
        { "${it.victory} ${if (it.victory < 2) "pt" else "pts"}" },
        { it.treasure }
    ),
    GOURMET(
        "Gourmet Hunter",
        "Raffinement",
        ":fork_knife_plate:",
        "\uD83C\uDF7D\uFE0F",
        "gourmet",
        { it.refinement.toDouble() },
        { "${it.refinement} ${if (it.refinement < 2) "pt" else "pts"}" },
        { it.gourmet }
    ),
    BEAST(
        "Beast Hunter",
        "Performance",
        ":t_rex:",
        "\uD83E\uDD96",
        "beast",
        { it.performance.toDouble() },
        { "${it.performance} ${if (it.performance < 2) "pt" else "pts"}" },
        { it.beast }
    ),
    BLACKLIST(
        "Blacklist Hunter",
        "Prouesse",
        ":skull_crossbones:",
        "☠\uFE0F",
        "blacklist",
        { it.achievement.toDouble() },
        { "${it.achievement} ${if (it.achievement < 2) "pt" else "pts"}" },
        { it.blacklist }
    ),
    HEAD(
        "Head Hunter",
        "Ratio",
        ":dart:",
        "\uD83C\uDFAF",
        "head",
        { it.pointsRatio() },
        { "${it.pointsRatio()} pts/partie" },
        { it.head }
    )
}
