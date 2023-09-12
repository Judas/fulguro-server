package com.fulgurogo.features.api

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import kotlin.math.roundToInt

data class ApiStability(
    val gameCount: Int = 0,
    val ladderGameCount: Int = 0,
    val deviation: Int = INITIAL_DEVIATION.roundToInt(),
    val period: Int = 30,
)
