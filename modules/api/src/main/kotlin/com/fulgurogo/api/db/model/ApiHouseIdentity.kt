package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.House

/**
 * A house's RP and nothing else — the answer to "which house am I in", as `POST /gold/api/house/join` returns it.
 *
 * A strict subset of [ApiHouse], down to the field names, so the site renders it with the same code. The figures are
 * left out rather than filled: a join answers with the house it drew, and a member count or a total read a moment later
 * would be a second question nobody asked. The `houses` route is where those live.
 *
 * The internal house id stays out of the API here as everywhere else: [slug] is the key the site works from.
 */
data class ApiHouseIdentity(
    val slug: String,
    val name: String,
    val tagline: String,
    /** Hex colour including the leading `#`. */
    val color: String,
    val description: String
) {
    companion object {
        fun from(house: House): ApiHouseIdentity = ApiHouseIdentity(
            slug = house.slug,
            name = house.name,
            tagline = house.tagline,
            color = house.color,
            description = house.description
        )
    }
}
