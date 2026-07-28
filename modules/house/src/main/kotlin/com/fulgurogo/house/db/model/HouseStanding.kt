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
