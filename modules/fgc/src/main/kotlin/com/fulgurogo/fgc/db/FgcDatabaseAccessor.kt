package com.fulgurogo.fgc.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.fgc.db.model.FgcValidity
import com.fulgurogo.fgc.db.model.FgcValidityGame
import org.sql2o.Connection
import org.sql2o.Sql2o

object FgcDatabaseAccessor {
    private const val VALIDITY_TABLE = "fgc_validity"
    private const val VALIDITY_GAMES_VIEW = "fgc_validity_games"

    private fun dao(): Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "total_games" to "totalGames",
            "total_ranked_games" to "totalRankedGames",
            "gold_games" to "goldGames",
            "gold_ranked_games" to "goldRankedGames",
            "gold_id" to "goldId",
            "black_discord_id" to "blackDiscordId",
            "white_discord_id" to "whiteDiscordId"
        )
    }

    fun stalestUser(): FgcValidity? = dao().open().use { connection ->
        val query = "SELECT * FROM $VALIDITY_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(FgcValidity::class.java)
    }

    fun markAsError(fgcValidity: FgcValidity): Connection = dao().open().use { connection ->
        val query = "UPDATE $VALIDITY_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", fgcValidity.discordId)
            .executeUpdate()
    }

    fun addPlayer(discordId: String): Connection = dao().open().use { connection ->
        val query = "INSERT INTO ${VALIDITY_TABLE}(discord_id, " +
                " total_games, total_ranked_games, gold_games, gold_ranked_games, " +
                " updated, error) " +
                " VALUES (:discordId, 0, 0, 0, 0, '2025-01-01 00:00:00', 0) " +
                " ON DUPLICATE KEY UPDATE " +
                " updated='2025-01-01 00:00:00' "
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeUpdate()
    }

    fun validityGames(fgcValidity: FgcValidity): List<FgcValidityGame> = dao().open().use { connection ->
        val query = "SELECT * FROM $VALIDITY_GAMES_VIEW " +
                " WHERE black_discord_id = :discordId OR white_discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("discordId", fgcValidity.discordId)
            .executeAndFetch(FgcValidityGame::class.java)
    }

    fun updateValidity(fgcValidity: FgcValidity): Connection = dao().open().use { connection ->
        val query = "UPDATE $VALIDITY_TABLE SET " +
                " total_games = :totalGames, " +
                " total_ranked_games = :totalRankedGames, " +
                " gold_games = :goldGames, " +
                " gold_ranked_games = :goldRankedGames, " +
                " updated = :updated, " +
                " error = 0 " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("totalGames", fgcValidity.totalGames)
            .addParameter("totalRankedGames", fgcValidity.totalRankedGames)
            .addParameter("goldGames", fgcValidity.goldGames)
            .addParameter("goldRankedGames", fgcValidity.goldRankedGames)
            .addParameter("updated", fgcValidity.updated)
            .addParameter("discordId", fgcValidity.discordId)
            .executeUpdate()
    }
}
