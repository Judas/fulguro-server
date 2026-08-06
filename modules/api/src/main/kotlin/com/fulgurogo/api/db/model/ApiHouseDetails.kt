package com.fulgurogo.api.db.model

import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.db.model.HouseDetails

/**
 * One house's page: the calendar, the house itself, and the ranking of its current members.
 *
 * [members] holds every current member, best first, including the ones who have scored nothing — they show up with
 * zeroes rather than being absent, since a house page that hides its quiet members is a roster that lies about its size.
 * A member with no Discord profile row is the one exception and is left out: nothing to display, no name and no avatar.
 * That is also why [ApiHouse.memberCount] can be greater than the length of this list.
 */
data class ApiHouseDetails(
    val period: HousePeriod,
    val season: String,
    val house: ApiHouse,
    val members: List<ApiHouseMember>
) {
    companion object {
        fun from(period: HousePeriod, season: String, details: HouseDetails): ApiHouseDetails = ApiHouseDetails(
            period = period,
            season = season,
            house = ApiHouse.from(details.standing),
            members = details.members.map { ApiHouseMember.from(it) }
        )
    }
}
