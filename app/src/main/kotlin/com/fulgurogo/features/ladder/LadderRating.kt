package com.fulgurogo.features.ladder

import com.fulgurogo.utilities.NoArg

@NoArg
data class LadderRating(
    val rating: Double,
    val deviation: Double,
    val volatility: Double,
    val tierRank: Int,
    val tierName: String
)
