package com.fulgurogo.features.user

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.games.GameScanListener
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.handleAllUsers
import com.fulgurogo.utilities.log
import net.dv8tion.jda.api.JDA

class UserService(private val jda: JDA) : GameScanListener {
    override fun onScanStarted() {
        log(INFO, "onScanStarted")
        handleAllUsers { rawUser ->
            // Update user profile info
            val user = rawUser.cloneUserWithUpdatedProfile(jda)
            DatabaseAccessor.updateUser(user)

            // Update unfinished games status
            DatabaseAccessor.gamesFor(user.discordId)
                .filter { !it.finished }
                .forEach {
                    // Check if game is now finished and update flag in db
                    val updatedGame = UserAccount.find(it.server)?.client?.userGame(user, it.serverId)
                    if (updatedGame?.isFinished() == true) DatabaseAccessor.updateFinishedGame(user, it.id, updatedGame)
                }
        }
    }

    override fun onScanFinished() {
        log(INFO, "onScanFinished")

        // Delete players who have left the server
        DatabaseAccessor.cleanOldUsers()
    }
}
