package com.fulgurogo.clean

import com.fulgurogo.clean.db.CleanDatabaseAccessor
import com.fulgurogo.common.service.PeriodicFlowService

class CleanService : PeriodicFlowService(300, 600) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // FIXME I REMOVED THIS FOR NOW, SEEMS TO TRIGGER TOO MUCH
//        // Delete everything related to users who have left the discord server
//        val phantomUsersIds = DiscordDatabaseAccessor.phantomUsers().map { it.discordId }
//        if (phantomUsersIds.isNotEmpty()) CleanDatabaseAccessor.removeAllFrom(phantomUsersIds)

        // Delete games older than a month or so
        CleanDatabaseAccessor.removeOldGames(32)

        // Delete some invalid accounts
        CleanDatabaseAccessor.removeDeletedAccounts()

        processing = false
    }
}
