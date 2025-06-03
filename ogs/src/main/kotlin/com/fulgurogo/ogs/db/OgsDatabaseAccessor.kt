package com.fulgurogo.ogs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.ogs.OgsModule.TAG
import com.fulgurogo.ogs.db.model.OgsGame
import com.fulgurogo.ogs.db.model.OgsUserInfo
import org.sql2o.Connection
import org.sql2o.Sql2o

object OgsDatabaseAccessor {
    private const val USER_TABLE = "ogs_user_info"
    private const val GAME_TABLE = "ogs_games"

    private val dao: Sql2o = DatabaseAccessor.dao().apply {
        // MySQL column name => POJO variable name
        defaultColumnMappings = mapOf(
            "discord_id" to "discordId",
            "ogs_id" to "ogsId",
            "ogs_name" to "ogsName",
            "ogs_rank" to "ogsRank",
            "black_id" to "blackId",
            "black_name" to "blackName",
            "black_rank" to "blackRank",
            "white_id" to "whiteId",
            "white_name" to "whiteName",
            "white_rank" to "whiteRank",
            "long_game" to "longGame"
        )
    }

    fun user(ogsId: Int): OgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE ogs_id = :ogsId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("ogsId", ogsId)
            .executeAndFetchFirst(OgsUserInfo::class.java)
    }

    fun stalestUser(): OgsUserInfo? = dao.open().use { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE error IS NULL ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(OgsUserInfo::class.java)
    }

    fun markAsError(ogsUserInfo: OgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET error = NOW() WHERE discord_id = :discordId "

        log(TAG, "markAsError [$query] $ogsUserInfo")
        connection
            .createQuery(query)
            .addParameter("discordId", ogsUserInfo.discordId)
            .executeUpdate()
    }

    fun updateUser(ogsUserInfo: OgsUserInfo): Connection = dao.open().use { connection ->
        val query = "UPDATE $USER_TABLE SET " +
                " ogs_name = :ogsName, " +
                " ogs_rank = :ogsRank, " +
                " updated = :updated " +
                " WHERE discord_id = :discordId "

        connection
            .createQuery(query)
            .addParameter("ogsName", ogsUserInfo.ogsName)
            .addParameter("ogsRank", ogsUserInfo.ogsRank)
            .addParameter("updated", ogsUserInfo.updated)
            .addParameter("discordId", ogsUserInfo.discordId)
            .executeUpdate()
    }

    fun game(game: OgsGame): OgsGame? = dao.open().use { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE id = :id LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("id", game.id)
            .executeAndFetchFirst(OgsGame::class.java)
    }

    fun addGame(game: OgsGame): Connection = dao.open().use { connection ->
        val query = "INSERT INTO $GAME_TABLE( " +
                " id, date, " +
                " black_id, black_name, black_rank, white_id, white_name, white_rank, " +
                " size, komi, handicap, long_game, result, sgf) " +
                " VALUES (:id, :date, " +
                " :blackId, :blackName, :blackRank, :whiteId, :whiteName, :whiteRank, " +
                " :size, :komi, :handicap, :longGame, :result, :sgf) "

        log(TAG, "addGame [$query] ${game.id}")

        connection
            .createQuery(query)
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
            .addParameter("longGame", game.longGame)
            .addParameter("result", game.result)
            .addParameter("sgf", game.sgf)
            .executeUpdate()
    }

    fun finishGame(game: OgsGame): Connection = dao.open().use { connection ->
        val query = "UPDATE $GAME_TABLE SET result = :result WHERE id = :id"

        log(TAG, "finishGame [$query] ${game.id}")

        connection
            .createQuery(query)
            .addParameter("result", game.result)
            .addParameter("id", game.id)
            .executeUpdate()
    }
}
