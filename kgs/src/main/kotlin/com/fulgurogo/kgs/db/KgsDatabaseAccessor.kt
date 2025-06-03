package com.fulgurogo.kgs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object KgsDatabaseAccessor {
    private const val USER_TABLE = "kgs_user_info"
    private const val GAME_TABLE = "kgs_games"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "kgs_id" to "kgsId",
            "kgs_rank" to "kgsRank",
            "black_name" to "blackName",
            "black_rank" to "blackRank",
            "white_name" to "whiteName",
            "white_rank" to "whiteRank",
            "long_game" to "longGame"
        )
    }

    fun user(kgsId: String): KgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE kgs_id = :kgsId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("kgsId", kgsId)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun stalestUser(): KgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE error IS NULL ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun markAsError(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET error = NOW() WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $kgsUserInfo")
        connection
            .createQuery(query)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
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

    fun game(game: KgsGame): KgsGame? = dao.open().use { connection ->
        val query = " SELECT * FROM $GAME_TABLE " +
                " WHERE  date = :date " +
                " AND black_name = :blackName " +
                " AND white_name = :whiteName " +
                " LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("date", game.date)
            .addParameter("blackName", game.blackName)
            .addParameter("whiteName", game.whiteName)
            .executeAndFetchFirst(KgsGame::class.java)
    }

    fun addGame(game: KgsGame): Connection = dao.open().use { connection ->
        val query = "INSERT INTO $GAME_TABLE( " +
                " date, " +
                " black_name, black_rank, white_name, white_rank, " +
                " size, komi, handicap, long_game, result, sgf) " +
                " VALUES (:date, " +
                " :blackName, :blackRank, :whiteName, :whiteRank, " +
                " :size, :komi, :handicap, :longGame, :result, :sgf) "

        log(TAG, "addGame [$query] ${game.date} ${game.blackName} ${game.whiteName}")

        connection
            .createQuery(query)
            .addParameter("date", game.date)
            .addParameter("blackName", game.blackName)
            .addParameter("blackRank", game.blackRank)
            .addParameter("whiteName", game.whiteName)
            .addParameter("whiteRank", game.whiteRank)
            .addParameter("size", game.size)
            .addParameter("komi", game.komi)
            .addParameter("handicap", game.handicap)
            .addParameter("longGame", game.longGame)
            .addParameter("result", game.result)
            .addParameter("sgf", game.sgf)
            .executeUpdate()
    }

    fun finishGame(game: KgsGame): Connection = dao.open().use { connection ->
        val query = "UPDATE $GAME_TABLE " +
                " SET result = :result " +
                " WHERE date = :date " +
                " AND black_name = :blackName " +
                " AND white_name = :whiteName "

        log(TAG, "finishGame [$query] ${game.date} ${game.blackName} ${game.whiteName}")

        connection
            .createQuery(query)
            .addParameter("date", game.date)
            .addParameter("blackName", game.blackName)
            .addParameter("whiteName", game.whiteName)
            .executeUpdate()
    }
}
