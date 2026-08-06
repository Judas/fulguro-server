package com.fulgurogo.api.db.model

import com.fulgurogo.house.db.model.HouseStanding

/**
 * A house as both house routes return it: its RP flat rather than nested, its figures, and its best current member.
 *
 * The same shape on the list and on the detail page, so the site can render one card either way. On the detail page
 * [leader] is the first entry of the ranking that comes with it — redundant, and kept for that consistency.
 *
 * [slug] is the stable machine key, and what the site builds the crest filename from. [name] is display-only.
 *
 * [totalPoints] and [memberCount] count different populations, deliberately: the total sums the register by house, so it
 * keeps the points of players who have since left, while the count and [leader] only see current members. The total is
 * therefore greater than or equal to the sum of the members' totals, and a house with nobody in it can still show
 * points.
 */
data class ApiHouse(
    val slug: String,
    val name: String,
    val tagline: String,
    /** Hex colour including the leading `#`. */
    val color: String,
    val description: String,
    val memberCount: Int,
    val totalPoints: Int,
    val leader: ApiHouseMember? = null
) {
    companion object {
        fun from(standing: HouseStanding): ApiHouse = ApiHouse(
            slug = standing.house.slug,
            name = standing.house.name,
            tagline = standing.house.tagline,
            color = standing.house.color,
            description = standing.house.description,
            memberCount = standing.memberCount,
            totalPoints = standing.totalPoints,
            leader = standing.leader?.let { ApiHouseMember.from(it) }
        )
    }
}
