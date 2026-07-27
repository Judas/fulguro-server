package com.fulgurogo.kgs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.logger.log
import com.fulgurogo.discord.GameStore
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo

object KgsDatabaseAccessor : GameStore<KgsGame> {
    private const val USER_TABLE = "kgs_user_info"
    private const val GAME_TABLE = "kgs_games"

    fun user(kgsId: String): KgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE kgs_id = :kgsId LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("kgsId", kgsId)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun addUser(discordId: String, kgsId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO $USER_TABLE(discord_id, kgs_id, kgs_rank, updated, error) " +
                    " VALUES (:discordId, :kgsId, '?', '2025-01-01 00:00:00', 0) "

            connection
                .query(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("kgsId", kgsId)
                .executeUpdate()
        }
    }

    fun stalestUser(): KgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun markAsError(kgsUserInfo: KgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("discordId", kgsUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateUser(kgsUserInfo: KgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET " +
                    " kgs_rank = :kgsRank, " +
                    " updated = :updated, " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .query(query)
                .addParameter("kgsRank", kgsUserInfo.kgsRank)
                .addParameter("updated", kgsUserInfo.updated)
                .addParameter("discordId", kgsUserInfo.discordId)
                .executeUpdate()
        }
    }

    override fun storedGame(game: KgsGame): KgsGame? = DatabaseAccessor.withDao { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE gold_id = :goldId LIMIT 1 "
        connection
            .query(query)
            .addParameter("goldId", game.goldId)
            .executeAndFetchFirst(KgsGame::class.java)
    }

    override fun isGoldGame(game: KgsGame): Boolean = user(game.blackId) != null && user(game.whiteId) != null

    /**
     * Inserts the game if it is not already known, keyed on the gold_id primary key.
     * @return true if this call is the one that created the row (i.e. the caller may notify).
     */
    override fun addGame(game: KgsGame): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "INSERT IGNORE INTO $GAME_TABLE( " +
                " gold_id, date, " +
                " black_id, black_rank, white_id, white_rank, " +
                " size, komi, handicap, ranked, long_game, result, sgf) " +
                " VALUES (:goldId, :date, " +
                " :blackId, :blackRank, :whiteId, :whiteRank, " +
                " :size, :komi, :handicap, :ranked, :longGame, :result, :sgf) "

        connection
            .query(query)
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

        val inserted = connection.result == 1
        log(TAG, "addGame ${game.goldId} inserted:$inserted")
        inserted
    }

    /**
     * Stamps the final result on a game that is still unfinished.
     * The unfinished check is part of the WHERE clause so that two concurrent callers cannot both win.
     * @return true if this call is the one that finished the row (i.e. the caller may notify).
     */
    override fun finishGame(game: KgsGame): Boolean = DatabaseAccessor.withDao { connection ->
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
