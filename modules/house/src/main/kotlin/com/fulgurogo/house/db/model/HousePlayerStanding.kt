package com.fulgurogo.house.db.model

/**
 * Where one player stands in their house this season: the house, their own line in its ranking, and what they asked for
 * next season. Composed in Kotlin like [HouseStanding], and read in one round trip.
 *
 * [standing] carries the points *and* the rank, both taken from the same ranking rather than the points being summed a
 * second time of their own. That is what makes the total a profile prints necessarily the total the rank was computed
 * from — two separate sums a connection apart could be straddling a scanner tick and disagree.
 *
 * [pendingAction] is the raw column value. Reading it outside the summer break says nothing: an intention is applied and
 * cleared when a season opens, so during a season it is null for everyone.
 *
 * [pendingHouse] is resolved here rather than carried as the id it is stored as, because the internal house id does not
 * leave this module and the API needs the slug.
 */
data class HousePlayerStanding(
    val house: House,
    val standing: HouseRankedMember,
    /** Raw `house_members.pending_action`: null, or one of the [com.fulgurogo.house.HouseAction] names. */
    val pendingAction: String? = null,
    /** The house a `CHANGE` names, resolved from `house_members.pending_house_id`. Null for any other intention. */
    val pendingHouse: House? = null
)
