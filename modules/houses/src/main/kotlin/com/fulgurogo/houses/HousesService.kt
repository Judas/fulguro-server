package com.fulgurogo.houses

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.houses.HousesModule.TAG
import com.fulgurogo.houses.db.HousesDatabaseAccessor

class HousesService : PeriodicFlowService(0, 5) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        HousesDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Get user new games since last check
                val newGames = HousesDatabaseAccessor.houseGames(stale)

                if (newGames.isNotEmpty()) {
                    // Compute points

//                    HouseUserInfo(
//                        discordId = stale.discordId,
//                        houseId = stale.houseId,
//                        played = stale.played,
//                        gold = stale.gold,
//                        house = stale.house,
//                        win = stale.win,
//                        long = stale.long,
//                        balanced = stale.balanced,
//                        ranked = stale.ranked,
//                        fgc = stale.fgc,
//                        checkedDate = stale.checkedDate,
//                        updated = Date(),
//                        error = false
//                    )
                }
                // Incrémenter les points
                // Save last game date as updated ?

                HousesDatabaseAccessor.updateUser(stale)
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                HousesDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
