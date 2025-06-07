package com.fulgurogo.gold

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.gold.GoldModule.TAG
import com.fulgurogo.gold.db.GoldDatabaseAccessor
import com.fulgurogo.gold.db.model.GoldPlayer
import java.util.*

class GoldService : PeriodicFlowService(0, 5) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        GoldDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Get user ranks
                val ranks = GoldDatabaseAccessor.userRanks(stale)
                if (ranks == null) throw IllegalStateException("User has no ranks")

                // Calculate player rating
                val rating = ranks.computeRating()
                if (rating == null) throw IllegalStateException("User has no rating")

                // Get corresponding tier rank
                val tier = GoldDatabaseAccessor.tierFor(rating)

                GoldDatabaseAccessor.updatePlayer(
                    GoldPlayer(
                        discordId = stale.discordId,
                        rating = rating,
                        tierRank = tier.rank,
                        updated = Date(),
                        error = null
                    )
                )

                // TODO Notify discord in case of update
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                GoldDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
