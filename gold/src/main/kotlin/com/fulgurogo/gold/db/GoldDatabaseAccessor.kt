package com.fulgurogo.gold.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.gold.GoldModule.TAG
import com.fulgurogo.gold.db.model.GoldPlayer
import com.fulgurogo.gold.db.model.GoldTier
import com.fulgurogo.gold.db.model.UserRanks
import org.sql2o.Connection
import org.sql2o.Sql2o

object GoldDatabaseAccessor {
    private const val TIERS_TABLE = "gold_tiers"
    private const val RATINGS_TABLE = "gold_ratings"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "tier_rank" to "tierRank",
            "kgs_rank" to "kgsRank",
            "ogs_rank" to "ogsRank",
            "fox_rank" to "foxRank",
            "igs_rank" to "igsRank",
            "ffg_rank" to "ffgRank",
            "egf_rank" to "egfRank"
        )
    }

    fun stalestUser(): GoldPlayer? = dao.open().use { connection ->
        val query = "SELECT * FROM $RATINGS_TABLE WHERE error IS NULL ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(GoldPlayer::class.java)
    }

    fun markAsError(goldPlayer: GoldPlayer): Connection = dao.open().use { connection ->
        val query = "UPDATE $RATINGS_TABLE SET error = NOW() WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $goldPlayer")
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", goldPlayer.discordId)
            .executeUpdate()
    }

    fun userRanks(stale: GoldPlayer): UserRanks? = dao.open().use { connection ->
        val query = "SELECT discord.discord_id, kgs_rank, ogs_rank, fox_rank, igs_rank, ffg_rank, egf_rank " +
                " FROM discord_user_info AS discord " +
                " LEFT JOIN kgs_user_info AS kgs ON discord.discord_id = kgs.discord_id " +
                " LEFT JOIN ogs_user_info AS ogs ON discord.discord_id = ogs.discord_id " +
                " LEFT JOIN fox_user_info AS fox ON discord.discord_id = fox.discord_id " +
                " LEFT JOIN igs_user_info AS igs ON discord.discord_id = igs.discord_id " +
                " LEFT JOIN ffg_user_info AS ffg ON discord.discord_id = ffg.discord_id " +
                " LEFT JOIN egf_user_info AS egf ON discord.discord_id = egf.discord_id " +
                " WHERE discord.discord_id = :discordId"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", stale.discordId)
            .executeAndFetchFirst(UserRanks::class.java)
    }

    fun tierFor(rating: Double): GoldTier = dao.open().use { connection ->
        val query = "SELECT rank FROM $TIERS_TABLE " +
                " WHERE (min <= :rating AND :rating < max) " +
                " LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("rating", rating)
            .executeAndFetchFirst(GoldTier::class.java)
    }

    fun updatePlayer(goldPlayer: GoldPlayer): Connection = dao.open().use { connection ->
        val query = "UPDATE $RATINGS_TABLE SET " +
                " rating = :rating, " +
                " tier_rank = :tierRank, " +
                " updated = :updated " +
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
