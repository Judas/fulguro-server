package com.fulgurogo.features.ladder.api

import com.fulgurogo.Config.Ladder.INITIAL_DEVIATION
import com.fulgurogo.Config.Ladder.INITIAL_RATING
import com.fulgurogo.features.games.Game
import kotlin.math.roundToInt

data class ApiRating(
    val rating: Int,
    val deviation: Int,
    val tierRank: Int,
    val tierName: String,
) {
    companion object {
        fun from(game: Game, black: Boolean, current: Boolean): ApiRating =
            when {
                black && current -> ApiRating(
                    rating = (game.blackCurrentRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.blackCurrentDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierRank = game.blackCurrentTierRank ?: 0,
                    tierName = game.blackCurrentTierName ?: "Division inconnue"
                )

                !black && current -> ApiRating(
                    rating = (game.whiteCurrentRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.whiteCurrentDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierRank = game.whiteCurrentTierRank ?: 0,
                    tierName = game.whiteCurrentTierName ?: "Division inconnue"
                )

                black && !current -> ApiRating(
                    rating = (game.blackHistoricalRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.blackHistoricalDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierRank = game.blackHistoricalTierRank ?: 0,
                    tierName = game.blackHistoricalTierName ?: "Division inconnue"
                )

                else -> ApiRating(
                    rating = (game.whiteHistoricalRating ?: INITIAL_RATING).roundToInt(),
                    deviation = (game.whiteHistoricalDeviation ?: INITIAL_DEVIATION).roundToInt(),
                    tierRank = game.whiteHistoricalTierRank ?: 0,
                    tierName = game.whiteHistoricalTierName ?: "Division inconnue"
                )
            }
    }
}
