package com.fulgurogo.kgs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.kgs.KgsModule.TAG
import com.fulgurogo.kgs.db.model.KgsGame
import com.fulgurogo.kgs.db.model.KgsUserInfo

object KgsDatabaseAccessor {
    private const val USER_TABLE = "kgs_user_info"
    private const val GAME_TABLE = "kgs_games"

    fun user(kgsId: String): KgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE kgs_id = :kgsId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("kgsId", kgsId)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun addUser(discordId: String, kgsId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO $USER_TABLE(discord_id, kgs_id, kgs_rank, updated, error) " +
                    " VALUES (:discordId, :kgsId, '?', '2025-01-01 00:00:00', 0) "

            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("kgsId", kgsId)
                .executeUpdate()
        }
    }

    fun stalestUser(): KgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(KgsUserInfo::class.java)
    }

    fun markAsError(kgsUserInfo: KgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .createQuery(query)
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
                .createQuery(query)
                .addParameter("kgsRank", kgsUserInfo.kgsRank)
                .addParameter("updated", kgsUserInfo.updated)
                .addParameter("discordId", kgsUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun game(game: KgsGame): KgsGame? = DatabaseAccessor.withDao { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE gold_id = :goldId LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("goldId", game.goldId)
            .executeAndFetchFirst(KgsGame::class.java)
    }

    fun addGame(game: KgsGame) {
        DatabaseAccessor.withDao { connection ->
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
    }

    fun finishGame(game: KgsGame) {
        DatabaseAccessor.withDao { connection ->
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
}
