package com.fulgurogo.features.ladder.api

import com.fulgurogo.features.ladder.LadderRating
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

data class ApiLadderPlayerRatings(
    val labels: List<String> = listOf(),
    val values: List<Int> = listOf(),
    val minValues: List<Int> = listOf(),
    val maxValues: List<Int> = listOf()
) {
    companion object {
        fun from(ratings: List<LadderRating>): ApiLadderPlayerRatings = ApiLadderPlayerRatings(
            ratings.map { SimpleDateFormat("d MMM").format(it.date) },
            ratings.map { it.rating.roundToInt() },
            ratings.map { (it.rating - it.deviation).roundToInt() },
            ratings.map { (it.rating + it.deviation).roundToInt() }
        )
    }
}
