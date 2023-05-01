package com.fulgurogo.features.ladder

data class LadderRating(
    val rating: Double,
    val deviation: Double,
    val volatility: Double,
    val tierRank: Int,
    val tierName: String
)
