package com.fulgurogo.fox.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.fox.FoxModule.TAG
import com.fulgurogo.fox.db.model.FoxGame
import com.fulgurogo.fox.db.model.FoxUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object FoxDatabaseAccessor {
    private const val USER_TABLE = "fox_user_info"
    private const val GAME_TABLE = "fox_games"

    private fun dao(): Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "gold_id" to "goldId",
            "fox_id" to "foxId",
            "fox_name" to "foxName",
            "fox_rank" to "foxRank",
            "black_id" to "blackId",
            "black_name" to "blackName",
            "black_rank" to "blackRank",
            "white_id" to "whiteId",
            "white_name" to "whiteName",
            "white_rank" to "whiteRank",
            "long_game" to "longGame"
        )
    }

    fun userById(foxId: Int): FoxUserInfo? = dao().open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE fox_id = :foxId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("foxId", foxId)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun user(foxName: Int): FoxUserInfo? = dao().open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE fox_name = :foxName LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("foxName", foxName)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }


    fun addUser(discordId: String, foxName: String): Connection = dao().open().use { connection ->
        val query = "INSERT INTO ${USER_TABLE}(discord_id, fox_id, fox_name, fox_rank, updated, error) " +
                " VALUES (:discordId, '?', :foxName, '?', '2025-01-01 00:00:00', 0) "

        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .addParameter("foxName", foxName)
            .executeUpdate()
    }

    fun stalestUser(): FoxUserInfo? = dao().open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun markAsError(foxUserInfo: FoxUserInfo): Connection = dao().open().use { connection ->
        val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("discordId", foxUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(foxUserInfo: FoxUserInfo): Connection = dao().open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
                " fox_id = :foxId, " +
                " fox_rank = :foxRank, " +
                " updated = :updated, " +
                " error = 0 " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("foxId", foxUserInfo.foxId)
            .addParameter("foxRank", foxUserInfo.foxRank)
            .addParameter("updated", foxUserInfo.updated)
            .addParameter("discordId", foxUserInfo.discordId)
            .executeUpdate()
    }

    fun game(game: FoxGame): FoxGame? = dao().open().use { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE gold_id = :goldId LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("goldId", game.goldId)
            .executeAndFetchFirst(FoxGame::class.java)
    }

    fun addGame(game: FoxGame): Connection = dao().open().use { connection ->
        val query = "INSERT INTO $GAME_TABLE( " +
                " gold_id, id, date, " +
                " black_id, black_name, black_rank, white_id, white_name, white_rank, " +
                " size, komi, handicap, ranked, long_game, result, sgf) " +
                " VALUES (:goldId, :id, :date, " +
                " :blackId, :blackName, :blackRank, :whiteId, :whiteName, :whiteRank, " +
                " :size, :komi, :handicap, :ranked, :longGame, :result, :sgf) "

        log(TAG, "addGame [$query] ${game.id}")

        connection
            .createQuery(query)
            .addParameter("goldId", game.goldId)
            .addParameter("id", game.id)
            .addParameter("date", game.date)
            .addParameter("blackId", game.blackId)
            .addParameter("blackName", game.blackName)
            .addParameter("blackRank", game.blackRank)
            .addParameter("whiteId", game.whiteId)
            .addParameter("whiteName", game.whiteName)
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

    fun finishGame(game: FoxGame): Connection = dao().open().use { connection ->
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
