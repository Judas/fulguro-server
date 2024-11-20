package com.fulgurogo.utilities

import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.Logger.Level.INFO
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

fun String.toRating(offset: Int = 0): Double? = when {
    contains("k") -> (30 - substring(0, indexOf("k")).trim().toInt() + offset).toDouble().toRating()
    contains("d") -> (substring(0, indexOf("d")).trim().toInt() + 29 + offset).toDouble().toRating()
    else -> null
}

fun Double.toRating(): Double = exp(this / C) * A

fun <T : UserAccountGame> Sequence<T>.filterGame(
    message: String,
    predicate: (T) -> Boolean
): Sequence<T> = filter {
    val condition = predicate.invoke(it)
    if (!condition) log(INFO, "Filtering game ${it.gameId()} because game $message.")
    condition
}
