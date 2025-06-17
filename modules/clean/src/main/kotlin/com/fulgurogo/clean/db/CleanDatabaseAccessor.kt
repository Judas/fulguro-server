package com.fulgurogo.clean.db

import com.fulgurogo.clean.CleanModule.TAG
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import org.sql2o.Sql2o

object CleanDatabaseAccessor {
    private val dao: Sql2o = DatabaseAccessor.dao().apply {
//        // MySQL column name => POJO variable name
//        defaultColumnMappings = mapOf(
//            "discord_id" to "discordId"
//        )
    }

    fun removeAllFrom(phantomUsersIds: List<String>) = dao.open().use { connection ->
        log(TAG, "removeAllFrom $phantomUsersIds")

        listOf(
            "discord_user_info",
            "kgs_user_info", "ogs_user_info", "fox_user_info",
            "igs_user_info", "ffg_user_info", "egf_user_info",
            "gold_ratings", "fgc_validity"
        ).forEach { table ->
            val query = "DELETE FROM $table WHERE discord_id IN (:ids)"
            connection
                .createQuery(query)
                .addParameter("ids", phantomUsersIds)
                .executeUpdate()
        }
    }

    fun removeOldGames(days: Int) = dao.open().use { connection ->
        log(TAG, "removeOldGames $days")

        listOf("kgs_games", "ogs_games", "fox_games").forEach { table ->
            val query = "DELETE FROM $table WHERE DATEDIFF(NOW(), date) > :days"
            connection
                .createQuery(query)
                .addParameter("days", days)
                .executeUpdate()
        }
    }

    fun removeDeletedAccounts() = dao.open().use { connection ->
        log(TAG, "removeDeletedAccounts")

        listOf(
            "DELETE FROM igs_user_info WHERE error = 1 AND igs_rank = '?'",
            "DELETE FROM ffg_user_info WHERE error = 1 AND ffg_name = ''",
            "DELETE FROM egf_user_info WHERE error = 1 AND egf_name = ''",
            "DELETE FROM ogs_user_info WHERE ogs_name LIKE 'deleted-%'"
        ).forEach {
            connection.createQuery(it).executeUpdate()
        }
    }
}
