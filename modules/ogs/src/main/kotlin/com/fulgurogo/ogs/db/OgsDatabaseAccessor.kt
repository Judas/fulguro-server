package com.fulgurogo.ogs.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.logger.log
import com.fulgurogo.ogs.OgsModule.TAG
import com.fulgurogo.ogs.db.model.OgsGame
import com.fulgurogo.ogs.db.model.OgsUserInfo

object OgsDatabaseAccessor {
    private const val USER_TABLE = "ogs_user_info"
    private const val GAME_TABLE = "ogs_games"

    fun user(ogsId: Int): OgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE WHERE ogs_id = :ogsId LIMIT 1"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .addParameter("ogsId", ogsId)
            .executeAndFetchFirst(OgsUserInfo::class.java)
    }

    fun addUser(discordId: String, ogsId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "INSERT INTO ${USER_TABLE}(discord_id, ogs_id, ogs_name, ogs_rank, updated, error) " +
                    " VALUES (:discordId, :ogsId, '?', '?', '2025-01-01 00:00:00', 0) "

            connection
                .createQuery(query)
                .throwOnMappingFailure(false)
                .addParameter("discordId", discordId)
                .addParameter("ogsId", ogsId)
                .executeUpdate()
        }
    }

    fun stalestUser(): OgsUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $USER_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(OgsUserInfo::class.java)
    }

    fun markAsError(ogsUserInfo: OgsUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .createQuery(query)
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
                .createQuery(query)
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
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(Int::class.java)
    }

    fun game(game: OgsGame): OgsGame? = DatabaseAccessor.withDao { connection ->
        val query = " SELECT * FROM $GAME_TABLE WHERE gold_id = :goldId LIMIT 1 "
        connection
            .createQuery(query)
            .addParameter("goldId", game.goldId)
            .executeAndFetchFirst(OgsGame::class.java)
    }

    fun addGame(game: OgsGame) {
        DatabaseAccessor.withDao { connection ->
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

            log(TAG, "addGame => ${connection.result}")


        }
    }

    fun finishGame(game: OgsGame) {
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
