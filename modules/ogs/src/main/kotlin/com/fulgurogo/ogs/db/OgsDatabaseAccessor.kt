package com.fulgurogo.ogs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.logger.log
import com.fulgurogo.discord.GameStore
import com.fulgurogo.ogs.OgsModule.TAG
import com.fulgurogo.ogs.db.model.OgsGame
import com.fulgurogo.ogs.db.model.OgsUserInfo

object OgsDatabaseAccessor : GameStore<OgsGame> {
    private const val USER_TABLE = "ogs_user_info"
    private const val GAME_TABLE = "ogs_games"

    fun user(ogsId: Int): OgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE ogs_id = :ogsId LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("ogsId", ogsId)
            .executeAndFetchFirst(OgsUserInfo::class.java)
    }

    fun addUser(discordId: String, ogsId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO ${USER_TABLE}(discord_id, ogs_id, ogs_name, ogs_rank, updated, error) " +
                    " VALUES (:discordId, :ogsId, '?', '?', '2025-01-01 00:00:00', 0) "

            connection
                .query(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("ogsId", ogsId)
                .executeUpdate()
        }
    }

    fun stalestUser(): OgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(OgsUserInfo::class.java)
    }

    fun markAsError(ogsUserInfo: OgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("discordId", ogsUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateUser(ogsUserInfo: OgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " ogs_name = :ogsName, " +
                    " ogs_rank = :ogsRank, " +
                    " updated = :updated, " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("ogsName", ogsUserInfo.ogsName)
                .addParameter("ogsRank", ogsUserInfo.ogsRank)
                .addParameter("updated", ogsUserInfo.updated)
                .addParameter("discordId", ogsUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun allUserIds(): List<Int> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT ogs_id FROM $USER_TABLE"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(Int::class.java)
    }

    override fun storedGame(game: OgsGame): OgsGame? = DatabaseAccessor.withDao { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE gold_id = :goldId LIMIT 1 "
        connection
            .query(query)
            .addParameter("goldId", game.goldId)
            .executeAndFetchFirst(OgsGame::class.java)
    }

    override fun isGoldGame(game: OgsGame): Boolean = user(game.blackId) != null && user(game.whiteId) != null

    /**
     * Inserts the game if it is not already known.
     * The OGS REST service and the OGS real time service both write this table, so the insert is made idempotent by
     * the gold_id primary key instead of by the caller's earlier [storedGame] lookup.
     * @return true if this call is the one that created the row (i.e. the caller may notify).
     */
    override fun addGame(game: OgsGame): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "INSERT IGNORE INTO $GAME_TABLE( " +
                " gold_id, id, date, " +
                " black_id, black_name, black_rank, white_id, white_name, white_rank, " +
                " size, komi, handicap, ranked, long_game, result, sgf) " +
                " VALUES (:goldId, :id, :date, " +
                " :blackId, :blackName, :blackRank, :whiteId, :whiteName, :whiteRank, " +
                " :size, :komi, :handicap, :ranked, :longGame, :result, :sgf) "

        connection
            .query(query)
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

        val inserted = connection.result == 1
        log(TAG, "addGame ${game.goldId} inserted:$inserted")
        inserted
    }

    /**
     * Stamps the final result on a game that is still unfinished.
     * The unfinished check is part of the WHERE clause so that two concurrent callers cannot both win.
     * @return true if this call is the one that finished the row (i.e. the caller may notify).
     */
    override fun finishGame(game: OgsGame): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $GAME_TABLE " +
                " SET result = :result, sgf = :sgf " +
                " WHERE gold_id = :goldId AND result = 'unfinished' "

        connection
            .query(query)
            .addParameter("result", game.result)
            .addParameter("sgf", game.sgf)
            .addParameter("goldId", game.goldId)
            .executeUpdate()

        val finished = connection.result == 1
        log(TAG, "finishGame ${game.goldId} finished:$finished")
        finished
    }
}
