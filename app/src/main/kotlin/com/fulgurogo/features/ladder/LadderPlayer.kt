package com.fulgurogo.features.ladder

import com.fulgurogo.utilities.NoArg
import java.util.*

@NoArg
data class LadderPlayer(
    val discordId: String,
    val ratingDate: Date? = null,
    val rating: Double,
    val deviation: Double,
    val volatility: Double,
    val ranked: Boolean,
) {
    fun clone(): LadderPlayer = LadderPlayer(discordId, ratingDate, rating, deviation, volatility, ranked)
}
