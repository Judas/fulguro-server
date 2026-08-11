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

    /**
     * Not this module's table, and reached into on purpose — see [removeAnnulledGames].
     *
     * `CleanDatabaseAccessor` sets the precedent: it deletes `house_members` from the clean module with raw SQL. Table
     * ownership is a convention here, not a boundary, and this is the second place that has a reason to cross it.
     */
    private const val HOUSE_POINTS_TABLE = "house_points"

    /**
     * Deletes games OGS has since voided, and the house points they wrongly earned. Answers how many games went.
     *
     * This closes a blind spot that was real, not theoretical: a league test game was annulled on 11 August 2026 and
     * stayed in `ogs_games` as a **win for white**, counted by FGC. The loop was closed the wrong way round —
     * `OgsRealTimeService` writes games from the live list and `OgsWsGameData` has no notion of annulment at all, while
     * this module's poll reads `annulled` but only ever used it to *skip*, so the writer that could not know wrote, and
     * the reader that knew merely declined to write. Nobody undid anything.
     *
     * Deleting rather than flagging, because that is what the rest of the application already understands: the row leaves
     * `house_games` and `fgc_validity_games` with it, and `FgcService` overwrites its counts from the view rather than
     * incrementing them, so a player's validity self-corrects on its next tick.
     *
     * ⚠ **Deleting from `house_points` is a deliberate exception** to "the register is never purged". That rule exists so
     * a house total cannot shrink when a player *leaves* — it is not a licence to keep points that were never earned. And
     * the alternative does not exist: orphan points cannot be found by absence later, because `CleanService` deletes every
     * game after 32 days, so "points whose game is gone" describes the whole register. The correction has to happen at the
     * moment the annulment is learnt, or never.
     *
     * Bounded by the poll window either way: a game annulled after it has left `ogs_games` is beyond reach, and that is
     * accepted.
     */
    fun removeAnnulledGames(goldIds: List<String>): Int {
        // `IN ()` is a syntax error in MySQL, and no annulled game is the normal case on nearly every tick.
        if (goldIds.isEmpty()) return 0

        return DatabaseAccessor.withDao { connection ->
            connection
                .query("DELETE FROM $GAME_TABLE WHERE gold_id IN (:goldIds)")
                .addParameter("goldIds", goldIds)
                .executeUpdate()
            val removed = connection.result

            connection
                .query("DELETE FROM $HOUSE_POINTS_TABLE WHERE gold_id IN (:goldIds)")
                .addParameter("goldIds", goldIds)
                .executeUpdate()
            val points = connection.result

            // Only when something actually went, or this would log on every tick that sees an old annulled game.
            if (removed > 0 || points > 0)
                log(TAG, "removeAnnulledGames removed $removed game(s) and $points house points row(s) for $goldIds")

            removed
        }
    }

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

    override fun trackedPlayerIds(): Set<String> = allUserIds().mapTo(mutableSetOf()) { it.toString() }

    override fun playerIds(game: OgsGame): Pair<String, String> =
        game.blackId.toString() to game.whiteId.toString()

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
