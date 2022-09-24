package com.fulgurogo.utilities

import kotlin.math.roundToInt

fun Double.formatDecimals(): Double = ((this * 100).roundToInt().toDouble()) / 100