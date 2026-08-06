package com.fulgurogo.clean

import com.fulgurogo.clean.CleanModule.TAG
import com.fulgurogo.clean.db.CleanDatabaseAccessor
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.discord.db.DiscordDatabaseAccessor

class CleanService : PeriodicFlowService(300, 600) {
    override suspend fun onTick() {
        // Delete everything related to users who have left the discord server
        removeUsersWhoLeft()

        // Delete games older than a month or so
        CleanDatabaseAccessor.removeOldGames(32)

        // Delete some invalid accounts
        CleanDatabaseAccessor.removeDeletedAccounts()
    }

    /**
     * Deletes users Discord confirmed left the guild at least [GRACE_PERIOD_IN_DAYS] day(s) ago.
     *
     * There is nothing to undo this with — a deleted user loses their discord row, both platform links, their rating,
     * their fgc validity and their house membership, and has to link everything again — so the two guards below stay
     * even though `DiscordService` is now the only writer of that flag and only writes it on an authoritative answer.
     *
     * Losing the house membership is on purpose: it is the only way out of a house mid-season, since the API only offers
     * "leave" during the summer break. The points earned for that house stay in the register.
     */
    private fun removeUsersWhoLeft() {
        // Dev runs against the production database, and in debug the bot cannot tell who is still on the server, so the
        // departure flags this reads are never the dev instance's to act on.
        if (Config.get("debug").toBoolean()) return

        val ids = DiscordDatabaseAccessor.usersWhoLeft(GRACE_PERIOD_IN_DAYS).map { it.discordId }
        if (ids.isEmpty()) return

        CleanDatabaseAccessor.removeAllFrom(ids)
    }

    companion object {
        /** How long a confirmed departure has to hold before it costs the user their data. Also covers leave-and-rejoin. */
        private const val GRACE_PERIOD_IN_DAYS = 1
    }
}
