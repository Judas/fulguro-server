package com.fulgurogo.kgs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import com.fulgurogo.kgs.db.model.KgsUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object KgsDatabaseAccessor {
    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "kgs_id" to "kgsId",
            "kgs_rank" to "kgsRank",
        )
    }

    fun stalestUser(): KgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM kgs_user_info ORDER BY updated"
        log(INFO, "stalestUser [$query]")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun updateUser(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE kgs_user_info SET " +
                " kgs_rank = :kgsRank, " +
                " stable = :stable, " +
                " updated = :updated " +
                " WHERE discord_id = :discordId "

        log(INFO, "updateUser [$query] $kgsUserInfo")
        connection
            .createQuery(query)
            .addParameter("kgsRank", kgsUserInfo.kgsRank)
            .addParameter("stable", kgsUserInfo.stable)
            .addParameter("updated", kgsUserInfo.updated)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }
}
