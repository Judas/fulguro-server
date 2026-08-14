package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.House

/**
 * A house reduced to its crest: the three fields a badge needs, and nothing else.
 *
 * Deliberately not [ApiHouse]. A twenty-player standings list would otherwise carry four houses' worth of tagline and
 * description repeated twenty times over, for a badge. The field names are the same ones, so the site styles it with the
 * code it already has.
 *
 * [slug] is the stable machine key and what the crest filename is built from; [name] is display-only.
 */
data class ApiLeagueCrest(
    val slug: String,
    val name: String,
    /** Hex colour including the leading `#`. */
    val color: String
) {
    companion object {
        fun from(house: House): ApiLeagueCrest = ApiLeagueCrest(
            slug = house.slug,
            name = house.name,
            color = house.color
        )
    }
}
