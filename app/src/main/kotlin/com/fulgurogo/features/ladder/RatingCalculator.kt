package com.fulgurogo.features.ladder

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.user.ServerUser
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.kgs.KgsUser
import com.fulgurogo.features.user.ogs.OgsUser
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import com.fulgurogo.utilities.toRating

object RatingCalculator {
    fun rate(accountMap: Map<UserAccount, ServerUser?>): Rating? {
        // Map ranks to ratings
        val ratingsMap: MutableMap<UserAccount, Double> = mutableMapOf()
        accountMap.entries
            .filter { it.value != null }
            .forEach {
                val rating: Double? =
                    when (it.key) {
                        UserAccount.OGS -> (it.value as OgsUser).fullRatings.live19.rating
                        UserAccount.KGS ->
                            if ((it.value as KgsUser).hasStableRank()) it.value?.rank()?.toRating()
                            else null

                        else -> it.value?.rank()?.toRating()
                    }

                if (rating != null)
                    ratingsMap[it.key] = rating
            }

        // Calculate average rank with server weights
        val totalWeight = ratingsMap.keys.sumOf { it.ratingWeight }
        if (totalWeight == 0.0) return null

        val weightedSum = ratingsMap.entries.sumOf { it.key.ratingWeight * it.value }
        val rating = weightedSum / totalWeight
        val tier = DatabaseAccessor.tierForRating(rating) ?: throw IllegalStateException()

        log(INFO, "rate $ratingsMap => $rating => ${tier.name}")

        return Rating(rating, tier)
    }
}