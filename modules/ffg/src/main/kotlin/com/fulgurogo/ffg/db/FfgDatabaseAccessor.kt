package com.fulgurogo.ffg.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.ffg.FfgModule.TAG
import com.fulgurogo.ffg.db.model.FfgUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object FfgDatabaseAccessor {
    private const val USER_TABLE = "ffg_user_info"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "ffg_id" to "ffgId",
            "ffg_name" to "ffgName",
            "ffg_rank" to "ffgRank"
        )
    }

    fun stalestUser(): FfgUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(FfgUserInfo::class.java)
    }

    fun markAsError(ffgUserInfo: FfgUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $ffgUserInfo")
        connection
            .createQuery(query)
            .addParameter("discordId", ffgUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(ffgUserInfo: FfgUserInfo): Connection = dao.open().use { connection ->
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
