package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.House

/**
 * A house reduced to its crest: the three fields a badge needs, and nothing else.
 *
 * Deliberately not [ApiHouse]. A list of players or of standings would otherwise carry four houses' worth of tagline
 * and description repeated down it, for a badge. The field names are the same ones, so the site styles it with the
 * code it already has.
 *
 * Used by the league standings, where it was first needed, and by the players list. It was called `ApiLeagueCrest`
 * while the league was its only caller — a house crest is what it always was.
 *
 * [slug] is the stable machine key and what the crest filename is built from; [name] is display-only.
 */
data class ApiHouseCrest(
    val slug: String,
    val name: String,
    /** Hex colour including the leading `#`. */
    val color: String
) {
    companion object {
        fun from(house: House): ApiHouseCrest = ApiHouseCrest(
            slug = house.slug,
            name = house.name,
            color = house.color
        )
    }
}
