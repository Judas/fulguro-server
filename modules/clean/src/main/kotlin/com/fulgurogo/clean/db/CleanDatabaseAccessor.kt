package com.fulgurogo.clean.db

import com.fulgurogo.clean.CleanModule.TAG
import com.fulgurogo.clean.db.model.PlayerPurgeReport
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.logger.log
import org.sql2o.Connection

object CleanDatabaseAccessor {
    /**
     * Every trace of an *account*, one table per line, and in the order they must go.
     *
     * `house_members` is first, and that is load-bearing rather than tidy — see [purgePlayer], which deletes
     * `house_points` after this list and would see the rows rewritten within the tick if the membership were still
     * standing. [removeAllFrom] does not care about the order, but sharing the one list is what stops the two paths
     * drifting the day a table is added.
     *
     * `house_members` belongs here — losing the Discord account means losing the membership, and it is in fact the only
     * way out of a house mid-season, since the API only offers "leave" during the summer break.
     *
     * `house_points` deliberately does **not**, and neither does `league_exemptions`; both are in [PURGE_ONLY_TABLES]
     * instead, where the reasons are set out. `league_matches` is in neither, and that omission is deliberate too:
     * deleting the matches would shrink an academy's renown and, worse, take the opponent's played-and-won credit with
     * them. A match is a fact about two players, and only one of them is being removed.
     *
     * Purging `league_players` loses nothing that cannot be rebuilt, and that is only true because the OGS member id is a
     * hash of the Discord id: it re-derives identically if the player ever comes back, so they find their OGS league
     * history again. The one thing lost is `ogs_registered`, which costs a repeated `PUT member/{id}` — a call OGS itself
     * describes as "register/update", so replaying it has no side effect. With a random uuid this same choice would
     * have cut the player off from their history. It is also what keeps the league from holding on to the Discord id of
     * someone who has left, when `discord_user_info`, `ogs_user_info`, `gold_ratings` and `house_members` are all purged.
     */
    private val ACCOUNT_TABLES = listOf(
        "house_members",
        "league_members", "league_players",
        "gold_ratings", "fgc_validity",
        "kgs_user_info", "ogs_user_info", "fox_user_info",
        "discord_user_info"
    )

    /**
     * What a purge removes on top of [ACCOUNT_TABLES], and `CleanService` pointedly does not.
     *
     * `house_points`: the rule that the register is never purged exists so a house total cannot shrink when a member
     * *leaves*. It is not a licence to keep points that should never have counted, which is the same exception
     * `OgsDatabaseAccessor.removeAnnulledGames` already makes in as many words. A ban is the other case that earns it.
     *
     * `league_exemptions`: `CleanService` keeps them because they are a fact about a *session* — the draw looked at this
     * player and found nobody — and the perfect-attendance bonus of anyone who comes back is computed from them. Nobody
     * comes back from a ban, and with the academy row gone the rows are read by nothing at all, so here they go.
     */
    private val PURGE_ONLY_TABLES = listOf("house_points", "league_exemptions")

    /**
     * Deletes a banned player: their account, their house points and their academy, in one transaction. Answers what went.
     *
     * The admin counterpart of [removeAllFrom], and it differs from it in exactly the two tables [PURGE_ONLY_TABLES]
     * names. Everything else about the two is the same, deliberately.
     *
     * **`league_matches` is not touched**, which is the whole of "without breaking the rest of the league". The row is
     * what credits the *opponent* with having played and won, `academyStandings` reads both its frozen house ids, and
     * `LeagueApiComposer.member` already answers for a Discord id the standings no longer hold — so the pairing stays on
     * display, named by a bare id, with the opponent's renown untouched. Deleting the row would quietly cost them two
     * points and possibly their perfect-attendance bonus.
     *
     * The games are not touched either, for the same reason one step further out: a game row is shared with the opponent,
     * so deleting it would take the house points they earned on it and their FGC validity with it. It leaves on its own
     * 32 days after it was played, like every other game.
     *
     * ⚠ They do still **disappear from the website**, and that surprises people, so it is worth stating rather than
     * discovering. `api_games` INNER JOINs both sides' `ogs_user_info`, `discord_user_info` and `gold_ratings`, so a game
     * loses its row in the view the moment either player's account goes — the opponent's game list on the site is one
     * shorter. `fgc_validity_games` LEFT JOINs instead, deliberately and with a comment in `doc/schema.sql` saying why, so
     * the opponent's validity keeps counting the game. Measured on `fg_dev`, not deduced. Nothing here can avoid it: it is
     * what removing the Discord account means, and `CleanService` has always done the same to anyone who leaves the guild.
     *
     * ⚠ The order inside the transaction is the correctness of the thing, not its style. `HouseDatabaseAccessor.gamesToScore`
     * marks its progress per *game* — `LEFT JOIN house_points ... WHERE p.gold_id IS NULL` — so a register row deleted while
     * the membership still stands puts the game straight back into a scanner that ticks every 30 seconds and re-scores it,
     * silently, because `addPoints` is an `INSERT IGNORE`. Dropping the membership first breaks the join the selection needs
     * and is what makes the deletion stick. [ACCOUNT_TABLES] is ordered for this and the transaction closes the window.
     */
    fun purgePlayer(discordId: String): PlayerPurgeReport {
        // Not `use`: sql2o's commit() and rollback() both close the connection themselves, so closing again afterwards
        // would be a second close. This is the pattern `LeagueDatabaseAccessor.writeDraw` documents.
        val connection = DatabaseAccessor.dao.beginTransaction()
        return try {
            val deleted = (ACCOUNT_TABLES + PURGE_ONLY_TABLES).associateWith { table ->
                deleteFrom(connection, table, discordId)
            }
            connection.commit()

            val report = PlayerPurgeReport(discordId, deleted)
            log(TAG, "purgePlayer $discordId removed ${report.total()} row(s): ${report.deleted.filterValues { it > 0 }}")
            report
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    private fun deleteFrom(connection: Connection, table: String, discordId: String): Int {
        connection
            .query("DELETE FROM $table WHERE discord_id = :discordId")
            .addParameter("discordId", discordId)
            .executeUpdate()
        return connection.result
    }

    /** Everything [ACCOUNT_TABLES] holds, for the users Discord confirmed left the guild. */
    fun removeAllFrom(phantomUsersIds: List<String>) {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeAllFrom $phantomUsersIds")

            ACCOUNT_TABLES.forEach { table ->
                val query = "DELETE FROM $table WHERE discord_id IN (:ids)"
                connection
                    .query(query)
                    .addParameter("ids", phantomUsersIds)
                    .executeUpdate()
            }
        }
    }

    fun removeOldGames(days: Int) {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeOldGames $days")

            listOf("kgs_games", "ogs_games", "fox_games").forEach { table ->
                val query = "DELETE FROM $table WHERE DATEDIFF(NOW(), date) > :days"
                connection
                    .query(query)
                    .addParameter("days", days)
                    .executeUpdate()
            }
        }
    }

    fun removeDeletedAccounts() {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeDeletedAccounts")

            connection
                .query("DELETE FROM ogs_user_info WHERE ogs_name LIKE 'deleted-%'")
                .executeUpdate()
        }
    }
}
