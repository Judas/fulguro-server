package com.fulgurogo.discord.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.discord.db.model.DiscordUserInfo

object DiscordDatabaseAccessor {
    private const val USER_TABLE = "discord_user_info"

    fun stalestUser(): DiscordUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(DiscordUserInfo::class.java)
    }

    fun user(discordId: String): DiscordUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE discord_id = :discordId"
        connection
            .query(query)
            // sql2o throws on an unmapped column by default, and this is a SELECT *: without this the next column added
            // to the table breaks this read until the app is redeployed. Every other read here already sets it.
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(DiscordUserInfo::class.java)
    }

    fun markAsError(discordUserInfo: DiscordUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("discordId", discordUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun createUser(discordId: String, discordName: String, discordAvatar: String) {
        DatabaseAccessor.withDao { connection ->
            val query =
                "INSERT INTO $USER_TABLE(discord_id, discord_name, discord_avatar, updated, error) " +
                        " VALUES (:discordId, :discordName, :discordAvatar, NOW(), 0) " +
                        " ON DUPLICATE KEY UPDATE " +
                        " discord_name=VALUES(discord_name), " +
                        " discord_avatar=VALUES(discord_avatar)"
            connection
                .query(query)
                .addParameter("discordId", discordId)
                .addParameter("discordName", discordName)
                .addParameter("discordAvatar", discordAvatar)
                .executeUpdate()
        }
    }

    /**
     * Records a profile read straight from Discord. Clearing `left_server_since` here is what lets someone who left and
     * came back stop being a candidate for deletion.
     */
    fun updateUser(discordUserInfo: DiscordUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " discord_name = :discordName, " +
                    " discord_avatar = :discordAvatar, " +
                    " updated = :updated, " +
                    " error = 0, " +
                    " left_server_since = NULL " +
                    " WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("discordName", discordUserInfo.discordName)
                .addParameter("discordAvatar", discordUserInfo.discordAvatar)
                .addParameter("updated", discordUserInfo.updated)
                .addParameter("discordId", discordUserInfo.discordId)
                .executeUpdate()
        }
    }

    /**
     * Starts the deletion clock for a user Discord has confirmed is gone. Only ever called on an authoritative answer,
     * never on a cache miss or a failed request — see `DiscordService.refresh`.
     *
     * `COALESCE` keeps the timestamp of the *first* confirmation, so repeated confirmations do not keep pushing the
     * grace period back and the row eventually becomes eligible for deletion. The name is deliberately left untouched:
     * a departure must not be readable from the profile.
     */
    fun markAsLeftServer(discordUserInfo: DiscordUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " updated = NOW(), " +
                    " error = 0, " +
                    " left_server_since = COALESCE(left_server_since, NOW()) " +
                    " WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("discordId", discordUserInfo.discordId)
                .executeUpdate()
        }
    }

    /**
     * Stamps `updated` and nothing else, to rotate the stalest-first queue past a user the bot cannot say anything
     * about. Notably it does not touch `left_server_since`, so an inconclusive tick neither starts nor resets the clock.
     */
    fun touchUser(discordUserInfo: DiscordUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW() WHERE discord_id = :discordId"

            connection
                .query(query)
                .addParameter("discordId", discordUserInfo.discordId)
                .executeUpdate()
        }
    }

    /** Users confirmed gone from the guild at least [days] days ago, i.e. the ones the clean module may delete. */
    fun usersWhoLeft(days: Int): List<DiscordUserInfo> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE " +
                " WHERE left_server_since IS NOT NULL " +
                " AND left_server_since <= DATE_SUB(NOW(), INTERVAL :days DAY)"
        connection
            .query(query)
            .addParameter("days", days)
            .throwOnMappingFailure(false)
            .executeAndFetch(DiscordUserInfo::class.java)
    }
}
