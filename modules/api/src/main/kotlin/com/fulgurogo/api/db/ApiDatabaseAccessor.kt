package com.fulgurogo.api.db

import com.fulgurogo.api.db.model.*
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import org.sql2o.Connection
import org.sql2o.Sql2o
import java.time.ZonedDateTime

object ApiDatabaseAccessor {
    private const val PLAYERS_VIEW = "api_players"
    private const val GAMES_VIEW = "api_games"

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
            "gold_ranked_games" to "goldRankedGames",
            "gold_id" to "goldId",
            "black_discord_id" to "blackDiscordId",
            "black_discord_name" to "blackDiscordName",
            "black_discord_avatar" to "blackDiscordAvatar",
            "black_rating" to "blackRating",
            "black_tier_rank" to "blackTierRank",
            "black_tier_name" to "blackTierName",
            "white_discord_id" to "whiteDiscordId",
            "white_discord_name" to "whiteDiscordName",
            "white_discord_avatar" to "whiteDiscordAvatar",
            "white_rating" to "whiteRating",
            "white_tier_rank" to "whiteTierRank",
            "white_tier_name" to "whiteTierName",
            "access_token" to "accessToken",
            "token_type" to "tokenType",
            "refresh_token" to "refreshToken",
            "expiration_date" to "expirationDate"
        )
    }

    fun apiPlayers(): List<ApiPlayer> = dao.open().use { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE rating > 0 ORDER by rating DESC"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiDbPlayer::class.java)
            ?.map { it.toApiPlayer() }
            ?: listOf()
    }

    fun apiPlayer(discordId: String): ApiPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE discord_id = :discordId"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiDbPlayer::class.java)
            ?.toApiPlayer()
    }

    fun recentGames(): List<ApiGame> = dao.open().use { connection ->
        val query = "SELECT * FROM $GAMES_VIEW ORDER BY date DESC LIMIT 20"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiDbGame::class.java)
            ?.map { it.toApiGame() }
            ?: listOf()
    }

    fun apiGamesFor(discordId: String): List<ApiGame> = dao.open().use { connection ->
        val query = "SELECT * FROM $GAMES_VIEW " +
                " WHERE black_discord_id = :discordId OR white_discord_id = :discordId " +
                " ORDER BY date DESC"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetch(ApiDbGame::class.java)
            ?.map { it.toApiGame() }
            ?: listOf()
    }

    fun apiGame(goldId: String): ApiGame? = dao.open().use { connection ->
        val query = "SELECT * FROM $GAMES_VIEW WHERE gold_id = :goldId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("goldId", goldId)
            .executeAndFetchFirst(ApiDbGame::class.java)
            ?.toApiGame()
    }

    fun saveAuthCredentials(goldId: String, authCredentials: AuthRequestResponse): Connection =
        dao.open().use { connection ->
            val query =
                "INSERT INTO auth_credentials(gold_id, access_token, token_type, refresh_token, expiration_date) " +
                        " VALUES (:gold_id, :access_token, :token_type, :refresh_token, :expiration_date) " +
                        " ON DUPLICATE KEY UPDATE " +
                        " access_token=VALUES(access_token), " +
                        " token_type=VALUES(token_type), " +
                        " refresh_token=VALUES(refresh_token), " +
                        " expiration_date=VALUES(expiration_date)"
            connection
                .createQuery(query)
                .addParameter("gold_id", goldId)
                .addParameter("access_token", authCredentials.access_token)
                .addParameter("token_type", authCredentials.token_type)
                .addParameter("refresh_token", authCredentials.refresh_token)
                .addParameter(
                    "expiration_date",
                    ZonedDateTime.now(DATE_ZONE).plusSeconds(authCredentials.expires_in).toDate()
                )
                .executeUpdate()
        }

    fun getAuthCredentials(goldId: String): AuthCredentials? = dao.open().use { connection ->
        val query = " SELECT * FROM auth_credentials WHERE gold_id = :goldId"
        connection
            .createQuery(query)
            .addParameter("goldId", goldId)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(AuthCredentials::class.java)
    }
}
