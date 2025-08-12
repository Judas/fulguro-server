package com.fulgurogo.houses.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.houses.db.model.HouseGame
import com.fulgurogo.houses.db.model.HouseUserInfo

object HousesDatabaseAccessor {
    private const val POINTS_TABLE = "fgo_house_points"
    private const val GAMES_VIEW = "fgo_houses_games"

//    private fun dao(): Sql2o = DatabaseAccessor.dao().apply {
//        // MySQL column name => POJO variable name
//        defaultColumnMappings = mapOf(
//            "discord_id" to "discordId",
//            "house_id" to "houseId",
//            "check_date" to "checkedDate",
//            "gold_id" to "goldId",
//            "black_discord_id" to "blackDiscordId",
//            "white_discord_id" to "whiteDiscordId",
//            "black_house_id" to "blackHouseId",
//            "white_house_id" to "whiteHouseId"
//        )
//    }

    fun stalestUser(): HouseUserInfo? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $POINTS_TABLE ORDER BY updated"
        connection
            .createQuery(query)
            .throwOnMappingFailure(false)
            .executeAndFetchFirst(HouseUserInfo::class.java)
    }

    fun markAsError(houseUserInfo: HouseUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $POINTS_TABLE SET updated = NOW(), error = 1 WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("discordId", houseUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateHousePoints(houseUserInfo: HouseUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $POINTS_TABLE SET " +
                    " played = :played, " +
                    " gold = :gold, house = :house, win = :win, " +
                    " long = :long, balanced = :balanced, ranked = :ranked, " +
                    " fgc = :fgc, checked_date = :checkedDate " +
                    " WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("played", houseUserInfo.played)
                .addParameter("gold", houseUserInfo.gold)
                .addParameter("house", houseUserInfo.house)
                .addParameter("win", houseUserInfo.win)
                .addParameter("long", houseUserInfo.long)
                .addParameter("balanced", houseUserInfo.balanced)
                .addParameter("ranked", houseUserInfo.ranked)
                .addParameter("fgc", houseUserInfo.fgc)
                .addParameter("checkedDate", houseUserInfo.checkedDate)
                .addParameter("discordId", houseUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun updateUser(houseUserInfo: HouseUserInfo) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $POINTS_TABLE SET " +
                    " updated = NOW(), " +
                    " error = 0 " +
                    " WHERE discord_id = :discordId "

            connection
                .createQuery(query)
                .addParameter("discordId", houseUserInfo.discordId)
                .executeUpdate()
        }
    }

    fun houseGames(houseUserInfo: HouseUserInfo): List<HouseGame> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $GAMES_VIEW " +
                " WHERE (black_discord_id = :discordId OR white_discord_id = :discordId) " +
                " AND date > :date"

        connection
            .createQuery(query)
            .addParameter("discordId", houseUserInfo.discordId)
            .addParameter("date", houseUserInfo.checkedDate)
            .executeAndFetch(HouseGame::class.java)
    }
}
