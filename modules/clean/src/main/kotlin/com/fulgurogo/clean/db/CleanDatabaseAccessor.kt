package com.fulgurogo.clean.db

import com.fulgurogo.clean.CleanModule.TAG
import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.common.logger.log

object CleanDatabaseAccessor {
    /**
     * Every trace of a player, one table per line.
     *
     * `house_members` belongs here — losing the Discord account means losing the membership, and it is in fact the only
     * way out of a house mid-season, since the API only offers "leave" during the summer break.
     *
     * `house_points` deliberately does **not**. Deleting a departed player's points would shrink their house's total,
     * which contradicts the rule that points stay earned for the house they were earned for. The register is meant to be
     * final, and a house's history should not quietly rewrite itself because someone left the server.
     *
     * `league_members` and `league_players` belong here, and `league_matches` does **not** — the same asymmetry as the
     * houses, for the same reason: deleting the matches would shrink an academy's renown.
     *
     * Purging `league_players` loses nothing that cannot be rebuilt, and that is only true because the OGS member id is a
     * hash of the Discord id: it re-derives identically if the player ever comes back, so they find their OGS league
     * history again. The one thing lost is `ogs_registered`, which costs a repeated `PUT member/{id}` — a call OGS itself
     * describes as "register/update", so replaying it has no side effect. With a random uuid this same choice would
     * have cut the player off from their history. It is also what keeps the league from holding on to the Discord id of
     * someone who has left, when `discord_user_info`, `ogs_user_info`, `gold_ratings` and `house_members` are all purged.
     */
    fun removeAllFrom(phantomUsersIds: List<String>) {
        DatabaseAccessor.withDao { connection ->
            log(TAG, "removeAllFrom $phantomUsersIds")

            listOf(
                "discord_user_info",
                "kgs_user_info", "ogs_user_info",
                "gold_ratings", "fgc_validity",
                "house_members",
                "league_members", "league_players"
            ).forEach { table ->
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

            listOf("kgs_games", "ogs_games").forEach { table ->
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
