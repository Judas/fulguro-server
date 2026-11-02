package com.fulgurogo.api.db.model

/**
 * The answer to `POST /gold/api/admin/house/points`: what was recorded, and the player's season total once it counts.
 *
 * [total] is read back from the ranking rather than computed from the request, so it is the figure the site will show.
 */
data class ApiHouseAdjustment(
    val discordId: String,
    /** The house slug the points went to — the player's house at the moment of the grant. */
    val house: String,
    val season: String,
    val points: Int,
    val reason: String,
    val total: Int
)
