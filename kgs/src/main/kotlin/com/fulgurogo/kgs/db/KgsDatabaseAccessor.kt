package com.fulgurogo.kgs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object KgsDatabaseAccessor {
    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "kgs_id" to "kgsId",
            "kgs_rank" to "kgsRank",
            "black_name" to "blackName",
            "black_rank" to "blackRank",
            "black_won" to "blackWon",
            "white_name" to "whiteName",
            "white_rank" to "whiteRank",
            "white_won" to "whiteWon",
            "long_game" to "longGame"
        )
    }

    fun stalestUser(): KgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM kgs_user_info WHERE error IS NULL ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun markAsError(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE kgs_user_info SET error = NOW() WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $kgsUserInfo")
        connection
            .createQuery(query)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE kgs_user_info SET " +
                " kgs_rank = :kgsRank, " +
                " updated = :updated " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("kgsRank", kgsUserInfo.kgsRank)
            .addParameter("updated", kgsUserInfo.updated)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }

    fun existGame(game: KgsGame): Boolean = dao.open().use { connection ->
        val query = " SELECT * FROM kgs_games " +
                " WHERE  date = :date " +
                " AND black_name = :blackName " +
                " AND white_name = :whiteName " +
                " LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("date", game.date)
            .addParameter("blackName", game.blackName)
            .addParameter("whiteName", game.whiteName)
            .executeAndFetchFirst(KgsGame::class.java) != null
    }

    fun addGame(game: KgsGame): Connection = dao.open().use { connection ->
        val query = "INSERT INTO kgs_games( " +
                " date, " +
                " black_name, black_rank, black_won, " +
                " white_name, white_rank, white_won, " +
                " size, komi, handicap, long_game, sgf) " +
                " VALUES (:date, " +
                " :blackName, :blackRank, :blackWon, " +
                " :whiteName, :whiteRank, :whiteWon, " +
                " :size, :komi, :handicap, :longGame, :sgf) "

        log(TAG, "addGame [$query] ${game.date} ${game.blackName} ${game.whiteName}")

        connection
            .createQuery(query)
            .addParameter("date", game.date)
            .addParameter("blackName", game.blackName)
            .addParameter("blackRank", game.blackRank)
            .addParameter("blackWon", game.blackWon)
            .addParameter("whiteName", game.whiteName)
            .addParameter("whiteRank", game.whiteRank)
            .addParameter("whiteWon", game.whiteWon)
            .addParameter("size", game.size)
            .addParameter("komi", game.komi)
            .addParameter("handicap", game.handicap)
            .addParameter("longGame", game.longGame)
            .addParameter("sgf", game.sgf)
            .executeUpdate()
    }
}
