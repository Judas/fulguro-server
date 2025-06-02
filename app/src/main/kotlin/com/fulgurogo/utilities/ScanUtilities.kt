package com.fulgurogo.utilities

import com.fulgurogo.TAG
import com.fulgurogo.common.logger.log
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.user.User

fun Any.forAllUsers(process: (User) -> Unit) {
    val users = DatabaseAccessor.usersWithLinkedPlayableAccount()
    val failedUsers = mutableListOf<User>()
    users.forEachIndexed { index, user ->
        try {
            log(TAG, "===================================")
            log(TAG, "[${index + 1}/${users.size}] Processing user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(TAG, "Error processing user ${user.discordId}", e)
            if (e !is InvalidUserException) failedUsers.add(user)
        }
    }

    // Try a second time for failed users (it may come from a timeout issue)
    failedUsers.forEachIndexed { index, user ->
        try {
            log(TAG, "===================================")
            log(TAG, "[${index + 1}/${failedUsers.size}] Processing failed for user ${user.discordId}")
            process(user)
        } catch (e: Exception) {
            log(TAG, "Failed again while processing user ${user.discordId}", e)
        }
    }
}
