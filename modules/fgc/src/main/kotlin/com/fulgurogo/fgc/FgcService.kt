package com.fulgurogo.fgc

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.fgc.FgcModule.TAG
import com.fulgurogo.fgc.db.FgcDatabaseAccessor
import com.fulgurogo.fgc.db.model.FgcValidity
import java.util.*

class FgcService : PeriodicFlowService(0, 15) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        FgcDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Get user games, gold or ranked
                val games = FgcDatabaseAccessor.validityGames(stale)
                FgcDatabaseAccessor.updateValidity(
                    FgcValidity(
                        discordId = stale.discordId,
                        totalGames = games.size,
                        totalRankedGames = games.count { it.ranked },
                        goldGames = games.count { it.blackDiscordId != null && it.whiteDiscordId != null },
                        goldRankedGames = games.count { it.blackDiscordId != null && it.whiteDiscordId != null && it.ranked },
                        updated = Date(),
                        error = false
                    )
                )
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                FgcDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
