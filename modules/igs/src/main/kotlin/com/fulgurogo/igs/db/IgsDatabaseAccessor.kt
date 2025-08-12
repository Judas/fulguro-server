package com.fulgurogo.igs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.igs.db.model.IgsUserInfo

object IgsDatabaseAccessor {
    private const val USER_TABLE = "igs_user_info"

    fun stalestUser(): IgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(IgsUserInfo::class.java)
    }

    fun user(igsId: Int): IgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE igs_id = :igsId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("igsId", igsId)
            .executeAndFetchFirst(IgsUserInfo::class.java)
    }

    fun addUser(discordId: String, igsId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO ${USER_TABLE}(discord_id, igs_id, igs_rank, updated, error) " +
                    " VALUES (:discordId, :igsId, '?', '2025-01-01 00:00:00', 0) "

            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("igsId", igsId)
                .executeUpdate()
        }
    }

    fun markAsError(igsUserInfo: IgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("discordId", igsUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateUser(igsUserInfo: IgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " igs_rank = :igsRank, " +
                    " updated = :updated, " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("igsRank", igsUserInfo.igsRank)
                .addParameter("updated", igsUserInfo.updated)
                .addParameter("discordId", igsUserInfo.discordId)
                .executeUpdate()
        }
    }
}
