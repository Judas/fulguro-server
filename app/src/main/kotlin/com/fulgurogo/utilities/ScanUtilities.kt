package com.fulgurogo.utilities

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.ladder.LadderPlayer
import com.fulgurogo.features.user.User
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO

fun Any.handleAllUsers(process: (User) -> Unit) {
    val users = DatabaseAccessor.usersWithLinkedPlayableAccount()
    val failedUsers = mutableListOf<User>()
    users.forEachIndexed { index, user ->
        try {
            log(INFO, "[${index + 1}/${users.size}] Processing user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(ERROR, "Error processing user ${user.discordId}", e)
            failedUsers.add(user)
        }
    }

    // Try a second time for failed users (it may come from a timeout issue)
    failedUsers.forEachIndexed { index, user ->
        try {
            log(INFO, "[${index + 1}/${failedUsers.size}] Processing failed user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(ERROR, "Failed again while processing games for user ${user.discordId}", e)
        }
    }
}

fun Any.handleAllLadderPlayers(process: (LadderPlayer) -> Unit) {
    val users = DatabaseAccessor.ladderPlayers()
    val failedUsers = mutableListOf<LadderPlayer>()
    users.forEachIndexed { index, user ->
        try {
            log(INFO, "[${index + 1}/${users.size}] Processing user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(ERROR, "Error processing user ${user.discordId}", e)
            failedUsers.add(user)
        }
    }

    // Try a second time for failed users (it may come from a timeout issue)
    failedUsers.forEachIndexed { index, user ->
        try {
            log(INFO, "[${index + 1}/${failedUsers.size}] Processing failed user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(ERROR, "Failed again while processing games for user ${user.discordId}", e)
        }
    }
}
