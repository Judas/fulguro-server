package com.fulgurogo.house.db.model

/**
 * A house as the "Houses" page shows it. Composed in Kotlin, not mapped from a row — sql2o cannot fill a nested object.
 *
 * [totalPoints] and [leader] are counted over different populations, and can disagree on purpose. The total sums the
 * register by house, so it keeps the points of players who have since left or changed house; [leader] only ever looks
 * at current members. A house total is therefore greater than or equal to the sum of its members' totals.
 *
 * [leader] is the top current member whatever they have scored, so at the start of a season it is a member with zero
 * points rather than null. Whether that is worth displaying is the website's call.
 */
data class HouseStanding(
    val house: House,
    val memberCount: Int,
    val totalPoints: Int,
    val leader: HouseRankedMember? = null
)

/**
 * The four houses best first — the one order in which houses are ever shown.
 *
 * Here rather than at each call site because there are three of them and they have to agree: the `houses` route, the
 * end-of-season recap and the daily ranking would otherwise each sort their own way, and a podium that changes between
 * the website and the bot is the kind of thing nobody reports as a bug.
 *
 * Ties go to the **smaller** house: an equal total reached with fewer players is the better performance, and it offsets
 * the standing advantage of simply having more people scoring. The name only settles a tie on both. Note that this makes
 * the order depend on [memberCount], which counts *current* members while [totalPoints] keeps the points of those who
 * left — so a departure can reshuffle two tied houses without either of them scoring.
 *
 * No rank is attached, unlike a member ranking. With four houses a tie is not unlikely, and a rank counted off the list
 * would print a 2nd and a 3rd where the truth is two 2nds — which is also why the tiebreaks here are for display order
 * only and do not claim one house beat the other.
 */
fun List<HouseStanding>.ranked(): List<HouseStanding> = sortedWith(
    compareByDescending<HouseStanding> { it.totalPoints }
        .thenBy { it.memberCount }
        .thenBy { it.house.name }
)
