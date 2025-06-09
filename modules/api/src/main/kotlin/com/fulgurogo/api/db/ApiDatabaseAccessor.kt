package com.fulgurogo.api.db

import com.fulgurogo.api.db.model.ApiAccount
import com.fulgurogo.api.db.model.ApiGame
import com.fulgurogo.api.db.model.ApiPlayer
import com.fulgurogo.api.db.model.ApiPlayerAccount
import com.fulgurogo.common.db.DatabaseAccessor
import org.sql2o.Sql2o

object ApiDatabaseAccessor {
    private const val PLAYERS_VIEW = "api_players"
    private const val ACCOUNTS_VIEW = "api_accounts"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "discord_name" to "discordName",
            "discord_avatar" to "discordAvatar",
            "kgs_id" to "kgsId",
            "kgs_rank" to "kgsRank",
            "ogs_id" to "ogsId",
            "ogs_name" to "ogsName",
            "ogs_rank" to "ogsRank",
            "fox_id" to "foxId",
            "fox_name" to "foxName",
            "fox_rank" to "foxRank",
            "igs_id" to "igsId",
            "igs_rank" to "igsRank",
            "ffg_id" to "ffgId",
            "ffg_name" to "ffgName",
            "ffg_rank" to "ffgRank",
            "egf_id" to "egfId",
            "egf_name" to "egfName",
            "egf_rank" to "egfRank",
            "tier_rank" to "tierRank",
            "tier_name" to "tierName",
            "total_ranked_games" to "totalRankedGames",
            "gold_ranked_games" to "goldRankedGames"
        )
    }

    fun apiPlayers(): List<ApiPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE rating > 0 ORDER by rating DESC"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiPlayer::class.java)
    }

    fun apiPlayer(discordId: String): ApiPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE discord_id = :discordId"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiPlayer::class.java)
    }

    fun apiAccountsFor(discordId: String): List<ApiPlayerAccount> = dao.open().use { connection ->
        val query = "SELECT * FROM $ACCOUNTS_VIEW WHERE discord_id = :discordId"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiAccount::class.java)
            ?.toApiPlayerAccounts()
            ?: listOf()
    }

//    fun apiGamesFor(discordId: String): List<ApiGame> = dao.open().use { connection ->
//        val query = "SELECT * FROM $ACCOUNTS_VIEW WHERE discord_id = :discordId"
//        connection
//            .createQuery(query)
//            .throwOnMappingFailure(false)
//            .addParameter("discordId", discordId)
//            .executeAndFetchFirst(ApiAccount::class.java)
//            ?.toApiPlayerAccounts()
//            ?: listOf()
//    }
}
