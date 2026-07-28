package com.fulgurogo.clean.db

import com.fulgurogo.clean.CleanModule.TAG
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.logger.log

object CleanDatabaseAccessor {
    fun removeAllFrom(phantomUsersIds: List<String>) {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeAllFrom $phantomUsersIds")

            listOf(
                "discord_user_info",
                "kgs_user_info", "ogs_user_info",
                "gold_ratings", "fgc_validity"
            ).forEach { table ->
                val query = "DELETE FROM $table WHERE discord_id IN (:ids)"
                connection
                    .query(query)
                    .addParameter("ids", phantomUsersIds)
                    .executeUpdate()
            }
        }
    }

    fun removeOldGames(days: Int) {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeOldGames $days")

            listOf("kgs_games", "ogs_games").forEach { table ->
                val query = "DELETE FROM $table WHERE DATEDIFF(NOW(), date) > :days"
                connection
                    .query(query)
                    .addParameter("days", days)
                    .executeUpdate()
            }
        }
    }

    fun removeDeletedAccounts() {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeDeletedAccounts")

            connection
                .query("DELETE FROM ogs_user_info WHERE ogs_name LIKE 'deleted-%'")
                .executeUpdate()
        }
    }
}
