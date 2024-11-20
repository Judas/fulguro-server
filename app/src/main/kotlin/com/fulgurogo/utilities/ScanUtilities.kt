package com.fulgurogo.utilities

import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.user.User
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO

fun Any.forAllUsers(process: (User) -> Unit) {
    val users = DatabaseAccessor.usersWithLinkedPlayableAccount()
    val failedUsers = mutableListOf<User>()
    users.forEachIndexed { index, user ->
        try {
            log(INFO, "===================================")
            log(INFO, "[${index + 1}/${users.size}] Processing user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(ERROR, "Error processing user ${user.discordId}", e)
            if (e !is InvalidUserException) failedUsers.add(user)
        }
    }

    // Try a second time for failed users (it may come from a timeout issue)
    failedUsers.forEachIndexed { index, user ->
        try {
            log(INFO, "===================================")
            log(INFO, "[${index + 1}/${failedUsers.size}] Processing failed for user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(ERROR, "Failed again while processing user ${user.discordId}", e)
        }
    }
}
