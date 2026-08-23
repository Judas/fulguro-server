package com.fulgurogo.fox.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.fox.api.FoxApiPlayer
import com.fulgurogo.fox.db.model.FoxGame
import com.fulgurogo.fox.db.model.FoxUserInfo

object FoxDatabaseAccessor {
    private const val USER_TABLE = "fox_user_info"
    private const val GAME_TABLE = "fox_games"

    fun userByFoxId(foxId: String): FoxUserInfo? = DatabaseAccessor.withDao { connection ->
        connection.query("SELECT * FROM $USER_TABLE WHERE fox_id = :foxId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("foxId", foxId)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun userByDiscordId(discordId: String): FoxUserInfo? = DatabaseAccessor.withDao { connection ->
        connection.query("SELECT * FROM $USER_TABLE WHERE discord_id = :discordId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun addUser(discordId: String, foxId: String, foxName: String, foxRank: String) {
        DatabaseAccessor.withDao { connection ->
            connection.query(
                "INSERT INTO $USER_TABLE(discord_id, fox_id, fox_name, fox_rank) " +
                    "VALUES (:discordId, :foxId, :foxName, :foxRank)"
            )
                .addParameter("discordId", discordId)
                .addParameter("foxId", foxId)
                .addParameter("foxName", foxName)
                .addParameter("foxRank", foxRank)
                .executeUpdate()
        }
    }

    fun stalestUser(): FoxUserInfo? = DatabaseAccessor.withDao { connection ->
        connection.query("SELECT * FROM $USER_TABLE ORDER BY updated IS NOT NULL, updated LIMIT 1")
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(FoxUserInfo::class.java)
    }

    fun markAsError(user: FoxUserInfo) {
        DatabaseAccessor.withDao { connection ->
            connection.query("UPDATE $USER_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId")
                .addParameter("discordId", user.discordId)
                .executeUpdate()
        }
    }

    fun markScanned(user: FoxUserInfo, player: FoxApiPlayer) {
        DatabaseAccessor.withDao { connection ->
            connection.query(
                "UPDATE $USER_TABLE SET total_win = :totalWin, total_lost = :totalLost, " +
                    "total_equal = :totalEqual, updated = NOW(), error = 0 WHERE discord_id = :discordId"
            )
                .addParameter("totalWin", player.totalwin)
                .addParameter("totalLost", player.totallost)
                .addParameter("totalEqual", player.totalequal)
                .addParameter("discordId", user.discordId)
                .executeUpdate()
        }
    }

    fun storedChessIds(chessIds: List<String>): Set<String> {
        if (chessIds.isEmpty()) return emptySet()
        return DatabaseAccessor.withDao { connection ->
            connection.query("SELECT chess_id FROM $GAME_TABLE WHERE chess_id IN (:chessIds)")
                .addParameter("chessIds", chessIds)
                .executeAndFetch(String::class.java)
                .toSet()
        }
    }

    fun addGame(game: FoxGame): Boolean = DatabaseAccessor.withDao { connection ->
        connection.query(
            "INSERT IGNORE INTO $GAME_TABLE(" +
                "gold_id, chess_id, date, black_id, black_name, black_rank, white_id, white_name, white_rank, " +
                "size, komi, handicap, ranked, long_game, result, sgf) " +
                "VALUES (:goldId, :chessId, :date, :blackId, :blackName, :blackRank, :whiteId, :whiteName, :whiteRank, " +
                ":size, :komi, :handicap, :ranked, :longGame, :result, :sgf)"
        )
            .addParameter("goldId", game.goldId)
            .addParameter("chessId", game.chessId)
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
        connection.result == 1
    }
}
