package com.fulgurogo.igs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.igs.IgsModule.TAG
import com.fulgurogo.igs.db.model.IgsUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object IgsDatabaseAccessor {
    private const val USER_TABLE = "igs_user_info"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "igs_id" to "igsId",
            "igs_rank" to "igsRank"
        )
    }

    fun stalestUser(): IgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE error IS NULL ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(IgsUserInfo::class.java)
    }

    fun markAsError(igsUserInfo: IgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET error = NOW() WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $igsUserInfo")
        connection
            .createQuery(query)
            .addParameter("discordId", igsUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(igsUserInfo: IgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
                " igs_rank = :igsRank, " +
                " updated = :updated " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("igsRank", igsUserInfo.igsRank)
            .addParameter("updated", igsUserInfo.updated)
            .addParameter("discordId", igsUserInfo.discordId)
            .executeUpdate()
    }
}
