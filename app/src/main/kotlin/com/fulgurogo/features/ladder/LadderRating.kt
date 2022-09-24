package com.fulgurogo.features.ladder

import java.util.*

data class LadderRating(
    val discordId: String,
    val date: Date,
    val rating: Double,
    val deviation: Double,
    val volatility: Double
)
