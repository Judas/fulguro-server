package com.fulgurogo.gold.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.gold.db.model.GoldPlayer
import com.fulgurogo.gold.db.model.GoldTier
import com.fulgurogo.gold.db.model.UserRanks

object GoldDatabaseAccessor {
    private const val TIERS_TABLE = "gold_tiers"
    private const val RATINGS_TABLE = "gold_ratings"
    private const val RANKS_VIEW = "gold_ranks"

    fun stalestUser(): GoldPlayer? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $RATINGS_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(GoldPlayer::class.java)
    }

    fun markAsError(goldPlayer: GoldPlayer) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $RATINGS_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", goldPlayer.discordId)
                .executeUpdate()
        }
    }

    fun userRanks(stale: GoldPlayer): UserRanks? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $RANKS_VIEW WHERE discord_id = :discordId"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", stale.discordId)
            .executeAndFetchFirst(UserRanks::class.java)
    }

    fun tiers(): List<GoldTier> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $TIERS_TABLE"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(GoldTier::class.java)
    }

    fun tierFor(rating: Double): GoldTier = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $TIERS_TABLE " +
                " WHERE (min <= :rating AND :rating < max) " +
                " LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("rating", rating)
            .executeAndFetchFirst(GoldTier::class.java)
    }

    fun addPlayer(discordId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO ${RATINGS_TABLE}(discord_id, rating, tier_rank, updated, error) " +
                    " VALUES (:discordId, 0, 1, '2025-01-01 00:00:00', 0) " +
                    " ON DUPLICATE KEY UPDATE " +
                    " updated='2025-01-01 00:00:00' "
            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    fun updatePlayer(goldPlayer: GoldPlayer) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $RATINGS_TABLE SET " +
                    " rating = :rating, " +
                    " tier_rank = :tierRank, " +
                    " updated = :updated, " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("rating", goldPlayer.rating)
                .addParameter("tierRank", goldPlayer.tierRank)
                .addParameter("updated", goldPlayer.updated)
                .addParameter("discordId", goldPlayer.discordId)
                .executeUpdate()
        }
    }
}
