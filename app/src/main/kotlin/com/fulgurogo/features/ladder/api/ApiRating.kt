package com.fulgurogo.features.ladder.api

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.ladder.LadderRating
import kotlin.math.roundToInt

data class ApiRating(
    val rating: Int,
    val deviation: Int,
    val tierName: String,
    val tierColor: String
) {
    companion object {
        fun default(): ApiRating {
            val tier = DatabaseAccessor.tierFor(INITIAL_RATING)
            return ApiRating(
                rating = INITIAL_RATING.roundToInt(),
                deviation = INITIAL_DEVIATION.roundToInt(),
                tierName = tier?.name ?: "Grade inconnu",
                tierColor = tier?.color ?: "#FF00FF"
            )
        }

        fun from(rating: LadderRating): ApiRating = ApiRating(
            rating = rating.rating.roundToInt(),
            deviation = rating.deviation.roundToInt(),
            tierName = rating.tierName,
            tierColor = rating.tierColor,
        )
    }
}
