package com.fulgurogo.api.db.model

import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.db.model.HouseStanding
import com.fulgurogo.house.db.model.ranked

/**
 * The "Houses" page: where the calendar stands, and the four houses best first.
 *
 * [period] and [season] travel with the houses rather than living in a route of their own, so the site never recomputes
 * the calendar nor pays for a second round trip. The server stays the only thing that knows when a season runs.
 *
 * [houses] is ordered by [ranked], the one order houses are ever shown in — shared with the bot's recap so the podium
 * cannot differ between the website and Discord. No rank is attached: with four houses a tie is not unlikely, and a
 * position counted off the list would print a 2nd and a 3rd where the truth is two 2nds.
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
            houses = standings.ranked().map { ApiHouse.from(it) }
        )
    }
}
