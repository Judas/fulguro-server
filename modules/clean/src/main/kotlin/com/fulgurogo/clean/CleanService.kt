package com.fulgurogo.clean

import com.fulgurogo.clean.db.CleanDatabaseAccessor
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.discord.db.DiscordDatabaseAccessor

class CleanService : PeriodicFlowService(0, 600) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Delete everything related to users who have left the discord server
        val phantomUsersIds = DiscordDatabaseAccessor.phantomUsers().map { it.discordId }
        if (phantomUsersIds.isNotEmpty()) CleanDatabaseAccessor.removeAllFrom(phantomUsersIds)

        // Delete games older than a month or so
        CleanDatabaseAccessor.removeOldGames(32)

        // Delete some invalid accounts
        CleanDatabaseAccessor.removeDeletedAccounts()

        processing = false
    }
}
