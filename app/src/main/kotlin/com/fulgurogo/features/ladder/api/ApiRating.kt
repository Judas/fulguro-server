package com.fulgurogo.features.ladder.api

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.Game
import kotlin.math.roundToInt

data class ApiRating(
    val rating: Int,
    val deviation: Int,
    val tierName: String,
    val tierBgColor: String,
    val tierFgColor: String
) {
    companion object {
        fun default(): ApiRating {
            val tier = DatabaseAccessor.tierFor(INITIAL_RATING)
            return ApiRating(
                rating = INITIAL_RATING.roundToInt(),
                deviation = INITIAL_DEVIATION.roundToInt(),
                tierName = tier?.name ?: "Grade inconnu",
                tierBgColor = tier?.bgColor ?: "#FF00FF",
                tierFgColor = tier?.fgColor ?: "#000000"
            )
        }

        fun from(game: Game, black: Boolean, current: Boolean): ApiRating =
            when {
                black && current -> ApiRating(
                    rating = (game.blackCurrentRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.blackCurrentDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierName = game.blackCurrentTierName ?: "Grade inconnu",
                    tierBgColor = game.blackCurrentTierBgColor ?: "#FF00FF",
                    tierFgColor = game.blackCurrentTierBgColor ?: "#000000"
                )

                !black && current -> ApiRating(
                    rating = (game.whiteCurrentRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.whiteCurrentDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierName = game.whiteCurrentTierName ?: "Grade inconnu",
                    tierBgColor = game.whiteCurrentTierBgColor ?: "#FF00FF",
                    tierFgColor = game.whiteCurrentTierBgColor ?: "#000000"
                )

                black && !current -> ApiRating(
                    rating = (game.blackHistoricalRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.blackHistoricalDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierName = game.blackHistoricalTierName ?: "Grade inconnu",
                    tierBgColor = game.blackHistoricalTierBgColor ?: "#FF00FF",
                    tierFgColor = game.blackHistoricalTierFgColor ?: "#000000"
                )

                else -> ApiRating(
                    rating = (game.whiteHistoricalRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.whiteHistoricalDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierName = game.whiteHistoricalTierName ?: "Grade inconnu",
                    tierBgColor = game.whiteHistoricalTierBgColor ?: "#FF00FF",
                    tierFgColor = game.whiteHistoricalTierFgColor ?: "#000000"
                )
            }
    }
}
