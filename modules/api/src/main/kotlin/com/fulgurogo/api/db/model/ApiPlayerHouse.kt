package com.fulgurogo.api.db.model

import com.fulgurogo.house.HouseAction
import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.db.model.HousePlayerStanding

/**
 * The house block of a player's profile: which house, what they have scored for it this season, and where that puts them
 * in it. Null on the profile of a player who is in no house.
 *
 * Deliberately not the [ApiHouse] of the house routes. It carries the RP a profile actually displays — a badge, a name, a
 * tagline, a colour — and leaves out the description and the house-wide figures, which belong to the house's own page and
 * would make every profile carry four paragraphs of lore. The field names it does share are the same ones, so the site
 * can style the badge with the same code.
 *
 * [rank] is a competition rank inside the house — equal totals share it and the next one skips (1, 2, 2, 4) — so it is a
 * figure to print, not a position to count.
 *
 * [period] and [season] ride along for the same reason they do on the house routes: the server stays the only thing that
 * knows when a season runs. [season] is also what [points] are counted over, so a profile can label them without
 * guessing, and [period] is what tells a null [pendingAction] apart from a member who simply has not chosen yet.
 */
data class ApiPlayerHouse(
    val period: HousePeriod,
    val season: String,
    val slug: String,
    val name: String,
    val tagline: String,
    /** Hex colour including the leading `#`. */
    val color: String,
    val points: ApiHousePoints,
    val rank: Int,
    /**
     * What the player asked for next season, and only during the summer break: outside it there is nothing to show, since
     * an intention is applied and cleared the moment a season opens. Null the rest of the year, and null all summer for a
     * member who has not chosen yet — [period] is what tells those two apart.
     */
    val pendingAction: HouseAction? = null,
    /**
     * The house a `CHANGE` will move the player to, so the site can name it back to them rather than only saying that
     * they asked to move. Null on every other intention, since none of them has a destination.
     *
     * Filled under the same condition as [pendingAction] and dropped with it outside the break. A crest and not an
     * [ApiHouseIdentity]: this names a house the player already read the lore of when they picked it.
     */
    val pendingHouse: ApiHouseCrest? = null
) {
    companion object {
        fun from(period: HousePeriod, season: String, standing: HousePlayerStanding): ApiPlayerHouse {
            // Parsed rather than echoed, so a value the column should never hold cannot reach the website as one, and
            // read once: the destination is only shown for the action it belongs to.
            val action = if (period == HousePeriod.VACATION) HouseAction.from(standing.pendingAction) else null

            return ApiPlayerHouse(
                period = period,
                season = season,
                slug = standing.house.slug,
                name = standing.house.name,
                tagline = standing.house.tagline,
                color = standing.house.color,
                points = ApiHousePoints.from(standing.standing),
                rank = standing.standing.rank,
                pendingAction = action,
                // Only on a CHANGE, whatever the column happens to hold: a destination shown next to a STAY or a LEAVE
                // would describe a move that is not going to happen.
                pendingHouse = standing.pendingHouse
                    ?.takeIf { action == HouseAction.CHANGE }
                    ?.let { ApiHouseCrest.from(it) }
            )
        }
    }
}
