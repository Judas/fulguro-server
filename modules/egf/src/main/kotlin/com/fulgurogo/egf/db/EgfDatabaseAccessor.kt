package com.fulgurogo.egf.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.egf.db.model.EgfUserInfo

object EgfDatabaseAccessor {
    private const val USER_TABLE = "egf_user_info"

    fun stalestUser(): EgfUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(EgfUserInfo::class.java)
    }

    fun user(egfId: Int): EgfUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE egf_id = :egfId LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("egfId", egfId)
            .executeAndFetchFirst(EgfUserInfo::class.java)
    }

    fun addUser(discordId: String, egfId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO ${USER_TABLE}(discord_id, egf_id, egf_name, egf_rank, updated, error) " +
                    " VALUES (:discordId, :egfId, '?', '?', '2025-01-01 00:00:00', 0) "

            connection
                .query(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("egfId", egfId)
                .executeUpdate()
        }
    }

    fun markAsError(egfUserInfo: EgfUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("discordId", egfUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateUser(egfUserInfo: EgfUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " egf_name = :egfName, " +
                    " egf_rank = :egfRank, " +
                    " updated = :updated, " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("egfName", egfUserInfo.egfName)
                .addParameter("egfRank", egfUserInfo.egfRank)
                .addParameter("updated", egfUserInfo.updated)
                .addParameter("discordId", egfUserInfo.discordId)
                .executeUpdate()
        }
    }
}
