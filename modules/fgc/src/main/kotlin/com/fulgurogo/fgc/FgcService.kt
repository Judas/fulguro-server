package com.fulgurogo.fgc

import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.fgc.FgcModule.TAG
import com.fulgurogo.fgc.db.FgcDatabaseAccessor
import com.fulgurogo.fgc.db.model.FgcValidity
import java.util.*

class FgcService : StalestFirstService<FgcValidity>(0, 15, TAG) {
    override fun stalest(): FgcValidity? = FgcDatabaseAccessor.stalestUser()

    override fun markAsError(stale: FgcValidity) = FgcDatabaseAccessor.markAsError(stale)

    override fun refresh(stale: FgcValidity) {
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
    }
}
