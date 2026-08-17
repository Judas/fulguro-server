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
    var house: ApiPlayerHouse? = null,
    /**
     * The player's house reduced to a badge, or null when they are in none. The mirror image of [house]: filled on
     * the **list** and left null on the profile, which already carries the full block.
     *
     * It exists because the list needed the one thing [house] was too heavy to give it. A roster does not want four
     * houses' worth of RP repeated down it, but it does want to show which house each player belongs to — three
     * fields, the same [ApiHouseCrest] the league standings already use for exactly that reason.
     */
    var crest: ApiHouseCrest? = null,
    /**
     * The player's league standing and matches, or null when they were never a member of the current season. Filled by
     * the profile route only, like [games] and [house].
     *
     * Composed by the handler for the same reason the house block is: it is counted over the current season, which only
     * Kotlin knows, so nothing has to be added to the `api_players` view in production.
     */
    var league: ApiPlayerLeague? = null
)
