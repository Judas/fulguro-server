package com.fulgurogo.egf.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.egf.db.model.EgfUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object EgfDatabaseAccessor {
    private const val USER_TABLE = "egf_user_info"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "egf_id" to "egfId",
            "egf_name" to "egfName",
            "egf_rank" to "egfRank"
        )
    }

    fun stalestUser(): EgfUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(EgfUserInfo::class.java)
    }

    fun user(egfId: Int): EgfUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE egf_id = :egfId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("egfId", egfId)
            .executeAndFetchFirst(EgfUserInfo::class.java)
    }

    fun addUser(discordId: String, egfId: String): Connection = dao.open().use { connection ->
        val query = "INSERT INTO ${USER_TABLE}(discord_id, egf_id, egf_name, egf_rank, updated, error) " +
                " VALUES (:discordId, :egfId, '?', '?', '2025-01-01 00:00:00', 0) "

        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .addParameter("egfId", egfId)
            .executeUpdate()
    }

    fun markAsError(egfUserInfo: EgfUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("discordId", egfUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(egfUserInfo: EgfUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
                " egf_name = :egfName, " +
                " egf_rank = :egfRank, " +
                " updated = :updated, " +
                " error = 0 " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("egfName", egfUserInfo.egfName)
            .addParameter("egfRank", egfUserInfo.egfRank)
            .addParameter("updated", egfUserInfo.updated)
            .addParameter("discordId", egfUserInfo.discordId)
            .executeUpdate()
    }
}
