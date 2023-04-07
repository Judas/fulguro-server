package com.fulgurogo.features.ladder

data class LadderRating(
    val rating: Double,
    val deviation: Double,
    val volatility: Double,
    val tierName: String,
    val tierColor: String
)
