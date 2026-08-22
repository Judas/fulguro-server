package com.fulgurogo.api.link

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class FoxUserInfo(
    val discordId: String,
    val foxId: String,
    val foxName: String,
    val foxRank: String,
)

object FoxDatabaseAccessor {
    private const val USER_TABLE = "fox_user_info"

    fun userByFoxId(foxId: String): FoxUserInfo? = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $USER_TABLE WHERE fox_id = :foxId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("foxId", foxId)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun userByDiscordId(discordId: String): FoxUserInfo? = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $USER_TABLE WHERE discord_id = :discordId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun addUser(discordId: String, account: ResolvedAccount) {
        DatabaseAccessor.withDao { connection ->
            connection
                .query(
                    "INSERT INTO $USER_TABLE(discord_id, fox_id, fox_name, fox_rank) " +
                        "VALUES (:discordId, :foxId, :foxName, :foxRank)"
                )
                .addParameter("discordId", discordId)
                .addParameter("foxId", account.id)
                .addParameter("foxName", account.name)
                .addParameter("foxRank", account.rank ?: "?")
                .executeUpdate()
        }
    }
}
