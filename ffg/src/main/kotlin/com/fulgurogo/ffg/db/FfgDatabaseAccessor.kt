package com.fulgurogo.kgs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.kgs.FfgModule.TAG
import com.fulgurogo.kgs.db.model.FfgUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object FfgDatabaseAccessor {
    private const val USER_TABLE = "ffg_user_info"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "ffg_id" to "ffgId",
            "ffg_rank" to "ffgRank"
        )
    }

    fun userById(ffgId: String): FfgUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE ffg_id = :ffgId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("ffgId", ffgId)
            .executeAndFetchFirst(FfgUserInfo::class.java)
    }

    fun stalestUser(): FfgUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE error IS NULL ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(FfgUserInfo::class.java)
    }

    fun markAsError(ffgUserInfo: FfgUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET error = NOW() WHERE discord_id = :discordId "

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
                " updated = :updated " +
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
