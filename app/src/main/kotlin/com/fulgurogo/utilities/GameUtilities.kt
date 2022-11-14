package com.fulgurogo.utilities

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.Config.Ladder.INITIAL_VOLATILITY
import com.fulgurogo.features.ladder.glicko.Glickotlin
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.*

private const val MIN_RATING = 100.0
private const val MAX_RATING = 6000.0
private const val A = 525.0
private const val C = 23.15
val FORMATTER = DecimalFormat("#.#", DecimalFormatSymbols().apply { decimalSeparator = '.' })

fun Double.toRank(): Double =
    ln(min(MAX_RATING, max(this, MIN_RATING)) / A) * C

fun Double.rankToString(withDecimal: Boolean): String = if (this < 30 && withDecimal) {
    FORMATTER.format(ceil((30 - this) * 10) / 10) + "k"
} else if (this < 30) {
    ceil(30 - this).roundToInt().toString() + "k"
} else if (withDecimal) {
    FORMATTER.format(floor((this - 29) * 10) / 10) + "d"
} else {
    floor(this - 29).roundToInt().toString() + "d"
}

// Fox Ranking
fun Int.rankToString(): String = if (this < 18) (18 - this).toString() + "k" else (this - 17).toString() + "d"

// Kyu/dan rank to int
// 30k > 30
// 1k > 1
// 1d > 0
// 5d > -4
// 7d > -6
fun String.toRankInt(): Int = if (contains("k"))
    substring(0, indexOf("k")).trim().toInt()
else
    substring(0, indexOf("d")).trim().toInt().minus(1).unaryMinus()

fun String.toRating(offset: Int): Double = when {
    contains("k") -> (30 - substring(0, indexOf("k")).trim().toInt() + offset).toDouble().toRating()
    contains("d") -> (substring(0, indexOf("d")).trim().toInt() + 29 + offset).toDouble().toRating()
    else -> INITIAL_RATING
}

fun Double.toRating(): Double = exp(this / C) * A

fun Glickotlin.Player?.averageWith(otherPlayer: Glickotlin.Player?): Glickotlin.Player =
    Glickotlin.Player(
        if (this == null && otherPlayer == null) INITIAL_RATING
        else if (this == null) otherPlayer!!.rating()
        else if (otherPlayer == null) rating()
        else arrayOf(rating(), otherPlayer.rating()).average(),
        INITIAL_DEVIATION,
        INITIAL_VOLATILITY
    )

fun Int?.toHandicapEmoji(): String = when (this) {
    9 -> ":nine:"
    8 -> ":eight:"
    7 -> ":seven:"
    6 -> ":six:"
    5 -> ":five:"
    4 -> ":four:"
    3 -> ":three:"
    2 -> ":two:"
    1 -> ":one:"
    0 -> ":zero:"
    else -> ":zero:"
}
