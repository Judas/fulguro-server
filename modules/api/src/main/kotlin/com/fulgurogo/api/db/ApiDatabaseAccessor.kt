package com.fulgurogo.api.db

import com.fulgurogo.api.db.model.*
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import java.time.ZonedDateTime

object ApiDatabaseAccessor {
    private const val PLAYERS_VIEW = "api_players"
    private const val GAMES_VIEW = "api_games"

    fun apiPlayers(): List<ApiPlayer> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE rating > 0 ORDER by rating DESC"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiDbPlayer::class.java)
            ?.map { it.toApiPlayer() }
            ?: listOf()
    }

    fun apiPlayer(discordId: String): ApiPlayer? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE discord_id = :discordId"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiDbPlayer::class.java)
            ?.toApiPlayer()
    }

    fun recentGames(): List<ApiGame> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $GAMES_VIEW ORDER BY date DESC LIMIT 20"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiDbGame::class.java)
            ?.map { it.toApiGame() }
            ?: listOf()
    }

    fun apiGamesFor(discordId: String): List<ApiGame> = DatabaseAccessor.withDao { connection ->
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

    fun apiGame(goldId: String): ApiGame? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $GAMES_VIEW WHERE gold_id = :goldId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("goldId", goldId)
            .executeAndFetchFirst(ApiDbGame::class.java)
            ?.toApiGame()
    }

    fun saveAuthCredentials(goldId: String, authCredentials: AuthRequestResponse) {
        DatabaseAccessor.withDao { connection ->
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
    }

    fun getAuthCredentials(goldId: String): AuthCredentials? = DatabaseAccessor.withDao { connection ->
        val query = " SELECT * FROM auth_credentials WHERE gold_id = :goldId"
        connection
            .createQuery(query)
            .addParameter("goldId", goldId)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(AuthCredentials::class.java)
    }
}
