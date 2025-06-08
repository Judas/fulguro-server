package com.fulgurogo.common.utilities

import kotlin.math.*

private const val MIN_RATING = 100.0
private const val MAX_RATING = 6000.0
private const val A = 525.0
private const val C = 23.15

// glicko rating to rank (1169.93 => 18.55)
fun Double.ratingToRank(): Double =
    ln(min(MAX_RATING, max(this, MIN_RATING)) / A) * C

// rank to rating (18.55 => 1169.93)
fun Double.rankToRating(): Double = exp(this / C) * A

// rank to kyu/dan string (18.55 => 12k)
fun Double.rankToKyuDanString(): String =
    if (this < 30) ceil(30 - this).roundToInt().toString() + "k"
    else floor(this - 29).roundToInt().toString() + "d"

// kyu/dan string to rank (12k => 18)
fun String.kyuDanStringToRank(): Double =
    if (contains("k")) (30 - substring(0, indexOf("k")).trim().toInt()).toDouble()
    else if (contains("d")) (29 + substring(0, indexOf("d")).trim().toInt()).toDouble()
    else throw IllegalArgumentException("Malformed kyu/dan string $this")
