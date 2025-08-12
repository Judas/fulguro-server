package com.fulgurogo.ffg.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.ffg.db.model.FfgUserInfo

object FfgDatabaseAccessor {
    private const val USER_TABLE = "ffg_user_info"

    fun stalestUser(): FfgUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(FfgUserInfo::class.java)
    }

    fun user(ffgId: Int): FfgUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE ffg_id = :ffgId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("ffgId", ffgId)
            .executeAndFetchFirst(FfgUserInfo::class.java)
    }

    fun addUser(discordId: String, ffgId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO ${USER_TABLE}(discord_id, ffg_id, ffg_name, ffg_rank, updated, error) " +
                    " VALUES (:discordId, :ffgId, '?', '?', '2025-01-01 00:00:00', 0) "

            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("ffgId", ffgId)
                .executeUpdate()
        }
    }

    fun markAsError(ffgUserInfo: FfgUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("discordId", ffgUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateUser(ffgUserInfo: FfgUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " ffg_name = :ffgName, " +
                    " ffg_rank = :ffgRank, " +
                    " updated = :updated, " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("ffgName", ffgUserInfo.ffgName)
                .addParameter("ffgRank", ffgUserInfo.ffgRank)
                .addParameter("updated", ffgUserInfo.updated)
                .addParameter("discordId", ffgUserInfo.discordId)
                .executeUpdate()
        }
    }
}
