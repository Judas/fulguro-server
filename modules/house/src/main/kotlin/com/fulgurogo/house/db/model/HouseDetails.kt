package com.fulgurogo.house.db.model

/**
 * Everything one house's page needs: its figures, and the ranking of its current members. Composed in Kotlin like
 * [HouseStanding], and read in a single round trip so the total and the ranking cannot come from two different moments.
 *
 * [members] is the same list [HouseStanding.leader] was taken from, best first, which is the only way the two are
 * guaranteed to agree: a leader read separately could name a player who is not first in the ranking shown next to them.
 */
data class HouseDetails(
    val standing: HouseStanding,
    val members: List<HouseRankedMember>
)
