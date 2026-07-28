package com.fulgurogo.gold.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import com.fulgurogo.common.utilities.kyuDanStringToRank
import com.fulgurogo.common.utilities.rankToRating

@GenerateNoArgConstructor
data class UserRanks(
    val discordId: String,
    val kgsRank: String?,
    val ogsRank: String?,
    val error: Boolean
) {
    fun computeRating(): Double? {
        if (error) return null

        // Translate ranks to rating with weight applied
        // KGS 0.8 - OGS 1.0

        val ranks = listOf(kgsRank to 0.8, ogsRank to 1.0)
            .filter { !it.first.isNullOrBlank() && it.first != "?" }

        // Total of applied weights
        val totalWeight = ranks.sumOf { it.second }
        if (totalWeight == 0.0) return null

        // Sum of weighted ratings
        val weightedSum = ranks.sumOf { it.second * it.first!!.kyuDanStringToRank().rankToRating() }

        // Compute average weighted rating
        return weightedSum / totalWeight
    }
}
