package com.fulgurogo.api.db

import com.fulgurogo.api.db.model.*
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import java.time.ZonedDateTime

object ApiDatabaseAccessor {
    private const val PLAYERS_VIEW = "api_players"
    private const val GAMES_VIEW = "api_games"

    fun apiPlayers(): List<ApiPlayer> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE rating > 0 ORDER by rating DESC"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(ApiDbPlayer::class.java)
            ?.map { it.toApiPlayer() }
            ?: listOf()
    }

    fun apiPlayer(discordId: String): ApiPlayer? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $PLAYERS_VIEW WHERE discord_id = :discordId"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(ApiDbPlayer::class.java)
            ?.toApiPlayer()
    }

    fun recentGames(): List<ApiGame> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $GAMES_VIEW ORDER BY date DESC LIMIT 20"
        connection
            .query(query)
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
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetch(ApiDbGame::class.java)
            ?.map { it.toApiGame() }
            ?: listOf()
    }

    fun apiGame(goldId: String): ApiGame? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $GAMES_VIEW WHERE gold_id = :goldId LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("goldId", goldId)
            .executeAndFetchFirst(ApiDbGame::class.java)
            ?.toApiGame()
    }

    /**
     * The members behind [ogsIds], keyed by OGS id. An id nobody linked is simply absent.
     *
     * LEFT JOINs on the rating, unlike `api_games`: a member who just linked is in a live game before GoldService has
     * rated them, and dropping them would drop the game.
     */
    fun ogsMembers(ogsIds: Collection<Int>): Map<Int, ApiOgsMember> {
        // `IN ()` is a syntax error in MySQL, and no live game is a normal answer
        if (ogsIds.isEmpty()) return mapOf()

        return DatabaseAccessor.withDao { connection ->
            val query = "SELECT ogs.ogs_id, discord.discord_id, discord.discord_name, discord.discord_avatar, " +
                    " gold.rating, gold.tier_rank, tier.name AS tier_name " +
                    " FROM ogs_user_info AS ogs " +
                    " INNER JOIN discord_user_info AS discord ON ogs.discord_id = discord.discord_id " +
                    " LEFT JOIN gold_ratings AS gold ON discord.discord_id = gold.discord_id " +
                    " LEFT JOIN gold_tiers AS tier ON gold.tier_rank = tier.rank " +
                    " WHERE ogs.ogs_id IN (:ogsIds)"
            connection
                .query(query)
                .throwOnMappingFailure(false)
                .addParameter("ogsIds", ogsIds.toList())
                .executeAndFetch(ApiOgsMember::class.java)
                ?.associateBy { it.ogsId }
                ?: mapOf()
        }
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
                .query(query)
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
            .query(query)
            .addParameter("goldId", goldId)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(AuthCredentials::class.java)
    }
}
