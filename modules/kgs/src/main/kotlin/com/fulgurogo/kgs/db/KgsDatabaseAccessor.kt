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
            "gold_id" to "goldId",
            "kgs_id" to "kgsId",
            "kgs_rank" to "kgsRank",
            "black_id" to "blackId",
            "black_rank" to "blackRank",
            "white_id" to "whiteId",
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
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun markAsError(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $kgsUserInfo")
        connection
            .createQuery(query)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(kgsUserInfo: KgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
                " kgs_rank = :kgsRank, " +
                " updated = :updated, " +
                " error = 0 " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("kgsRank", kgsUserInfo.kgsRank)
            .addParameter("updated", kgsUserInfo.updated)
            .addParameter("discordId", kgsUserInfo.discordId)
            .executeUpdate()
    }

    fun game(game: KgsGame): KgsGame? = dao.open().use { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE gold_id = :goldId LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("goldId", game.goldId)
            .executeAndFetchFirst(KgsGame::class.java)
    }

    fun addGame(game: KgsGame): Connection = dao.open().use { connection ->
        val query = "INSERT INTO $GAME_TABLE( " +
                " gold_id, date, " +
                " black_id, black_rank, white_id, white_rank, " +
                " size, komi, handicap, ranked, long_game, result, sgf) " +
                " VALUES (:goldId, :date, " +
                " :blackId, :blackRank, :whiteId, :whiteRank, " +
                " :size, :komi, :handicap, :ranked, :longGame, :result, :sgf) "

        log(TAG, "addGame [$query] ${game.goldId}")

        connection
            .createQuery(query)
            .addParameter("goldId", game.goldId)
            .addParameter("date", game.date)
            .addParameter("blackId", game.blackId)
            .addParameter("blackRank", game.blackRank)
            .addParameter("whiteId", game.whiteId)
            .addParameter("whiteRank", game.whiteRank)
            .addParameter("size", game.size)
            .addParameter("komi", game.komi)
            .addParameter("handicap", game.handicap)
            .addParameter("ranked", game.ranked)
            .addParameter("longGame", game.longGame)
            .addParameter("result", game.result)
            .addParameter("sgf", game.sgf)
            .executeUpdate()
    }

    fun finishGame(game: KgsGame): Connection = dao.open().use { connection ->
        val query = "UPDATE $GAME_TABLE " +
                " SET result = :result, sgf = :sgf " +
                " WHERE gold_id = :goldId"
        log(TAG, "finishGame [$query] ${game.goldId}")

        connection
            .createQuery(query)
            .addParameter("result", game.result)
            .addParameter("sgf", game.sgf)
            .addParameter("goldId", game.goldId)
            .executeUpdate()
    }
}
