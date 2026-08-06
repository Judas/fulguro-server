package com.fulgurogo.api.db.model

import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.db.model.HouseStanding

/**
 * The "Houses" page: where the calendar stands, and the four houses best first.
 *
 * [period] and [season] travel with the houses rather than living in a route of their own, so the site never recomputes
 * the calendar nor pays for a second round trip. The server stays the only thing that knows when a season runs.
 *
 * [houses] is sorted on the total, ties broken on the name — the same order, and the same tiebreak, as a house's member
 * ranking. No rank is attached: with four houses a tie is not unlikely, and a position counted off the list would print
 * a 2nd and a 3rd where the truth is two 2nds.
 */
data class ApiHouses(
    val period: HousePeriod,
    val season: String,
    val houses: List<ApiHouse>
) {
    companion object {
        fun from(period: HousePeriod, season: String, standings: List<HouseStanding>): ApiHouses = ApiHouses(
            period = period,
            season = season,
            houses = standings
                .sortedWith(compareByDescending<HouseStanding> { it.totalPoints }.thenBy { it.house.name })
                .map { ApiHouse.from(it) }
        )
    }
}
