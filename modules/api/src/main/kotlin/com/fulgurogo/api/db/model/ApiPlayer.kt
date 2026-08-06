package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiPlayer(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val accounts: List<ApiPlayerAccount>? = null,
    val rating: Double = 0.0,
    val tierRank: Int = 0,
    val tierName: String? = null,
    val totalRankedGames: Int = 0,
    val goldRankedGames: Int = 0,
    var games: List<ApiGame>? = null,
    /**
     * The player's house, or null when they are in none. Filled by the profile route only, like [games], and left null on
     * the list — a roster of every player does not need four houses' worth of RP repeated down it.
     *
     * Composed by the handler from `HouseDatabaseAccessor`, not added to the `api_players` view: it is counted over the
     * current season, which only Kotlin knows, and this way there is no view to alter on the production server.
     */
    var house: ApiPlayerHouse? = null
)
