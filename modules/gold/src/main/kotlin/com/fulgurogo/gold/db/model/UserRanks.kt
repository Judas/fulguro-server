package com.fulgurogo.gold.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import com.fulgurogo.common.utilities.kyuDanStringToRank
import com.fulgurogo.common.utilities.rankToRating

@GenerateNoArgConstructor
data class UserRanks(
    val discordId: String,
    val kgsRank: String?,
    val ogsRank: String?,
    val foxRank: String?,
    val igsRank: String?,
    val ffgRank: String?,
    val egfRank: String?
) {
    fun computeRating(): Double? {
        // Translate ranks to rating with weight applied
        // KGS 0.8 - OGS 1.0 - FOX 0.1 - IGS 0.6 - FFG 0.7 - EGF 0.7

        val ranks =
            listOf(kgsRank to 0.8, ogsRank to 1.0, foxRank to 0.1, igsRank to 0.6, ffgRank to 0.7, egfRank to 0.7)
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
