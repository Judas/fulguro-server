package com.fulgurogo.gold.db.model

import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import com.fulgurogo.common.utilities.kyuDanStringToRank
import com.fulgurogo.common.utilities.rankToRating
import com.fulgurogo.common.utilities.toZonedDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max

private const val KGS_WEIGHT = 0.8
private const val OGS_WEIGHT = 1.0

@GenerateNoArgConstructor
data class UserRanks(
    val discordId: String,
    val kgsRank: String?,
    val kgsRankDate: Date?,
    val ogsRank: String?,
    val error: Boolean
) {
    fun computeRating(): Double? {
        if (error) return null

        // Translate ranks to rating with weight applied
        // KGS 0.8, faded by the age of the rank - OGS 1.0

        val ranks = listOf(kgsRank to kgsWeight(), ogsRank to OGS_WEIGHT)
            .filter { !it.first.isNullOrBlank() && it.first != "?" && it.second > 0.0 }

        // Total of applied weights
        val totalWeight = ranks.sumOf { it.second }
        if (totalWeight == 0.0) return null

        // Sum of weighted ratings
        val weightedSum = ranks.sumOf { it.second * it.first!!.kyuDanStringToRank().rankToRating() }

        // Compute average weighted rating
        return weightedSum / totalWeight
    }

    /**
     * The KGS weight, faded by how old the rank is.
     *
     * KGS publishes no current rank, only the one a player held in each archived game, and this community plays there
     * rarely enough that the stored rank can be years old (see `KgsService.scrapRank`). Trusting a four-year-old rank
     * as much as today's OGS rating would be wrong, and dropping it would leave KGS contributing nothing at all --
     * which is what happened for years. So it counts fully for its first year, then loses a fifth of its weight per
     * further year, and nothing from five years on.
     *
     * A rank with no date is one written before the column existed, so it counts as current; the next refresh dates it.
     */
    private fun kgsWeight(): Double {
        val years = kgsRankDate
            ?.let { ChronoUnit.YEARS.between(it.toZonedDateTime(), ZonedDateTime.now(DATE_ZONE)) }
            ?.coerceAtLeast(0)
            ?: 0

        return KGS_WEIGHT * max(0.0, 1.0 - 0.2 * years)
    }
}
