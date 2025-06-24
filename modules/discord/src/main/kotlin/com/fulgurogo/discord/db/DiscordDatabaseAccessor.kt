package com.fulgurogo.discord.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.discord.db.model.DiscordUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object DiscordDatabaseAccessor {
    private const val USER_TABLE = "discord_user_info"

    private fun dao(): Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "discord_name" to "discordName",
            "discord_avatar" to "discordAvatar"
        )
    }

    fun stalestUser(): DiscordUserInfo? = dao().open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(DiscordUserInfo::class.java)
    }

    fun user(discordId: String): DiscordUserInfo? = dao().open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE discord_id = :discordId"
        connection
            .createQuery(query)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(DiscordUserInfo::class.java)
    }

    fun markAsError(kgsUserInfo: DiscordUserInfo): Connection = dao().open().use { connection ->
        val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }

    fun createUser(discordId: String, discordName: String, discordAvatar: String): Connection =
        dao().open().use { connection ->
            val query =
                "INSERT INTO $USER_TABLE(discord_id, discord_name, discord_avatar, updated, error) " +
                        " VALUES (:discordId, :discordName, :discordAvatar, NOW(), 0) " +
                        " ON DUPLICATE KEY UPDATE " +
                        " discord_name=VALUES(discord_name), " +
                        " discord_avatar=VALUES(discord_avatar)"
            connection
                .createQuery(query)
                .addParameter("discordId", discordId)
                .addParameter("discordName", discordName)
                .addParameter("discordAvatar", discordAvatar)
                .executeUpdate()
        }

    fun updateUser(discordUserInfo: DiscordUserInfo): Connection = dao().open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
                " discord_name = :discordName, " +
                " discord_avatar = :discordAvatar, " +
                " updated = :updated, " +
                " error = 0 " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("discordName", discordUserInfo.discordName)
            .addParameter("discordAvatar", discordUserInfo.discordAvatar)
            .addParameter("updated", discordUserInfo.updated)
            .addParameter("discordId", discordUserInfo.discordId)
            .executeUpdate()
    }

    fun phantomUsers(): List<DiscordUserInfo> = dao().open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE discord_id = discord_name"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(DiscordUserInfo::class.java)
    }
}
