package com.fulgurogo.house

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import java.time.ZonedDateTime

/**
 * This module is in charge of the Houses: membership, points scoring and season transitions.
 */
object HouseModule {
    const val TAG = "HSE"

    private val housePointsService = HousePointsService()
    private val houseSeasonService = HouseSeasonService()

    fun init() {
        logCalendar()
        housePointsService.start()
        houseSeasonService.start()
    }

    /**
     * One line at startup with the calendar the module is going to work from.
     *
     * Both values are asked for the same instant rather than each calling [ZonedDateTime.now] on its own, so the line
     * cannot show a period and a season read on either side of a month boundary. It also forces [HouseSeason] to
     * initialise here, which is what makes its override warning appear at startup rather than on first use.
     */
    private fun logCalendar() {
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)
        val (start, end) = HouseSeason.seasonWindow(season)
        log(TAG, "Period is ${HouseSeason.period(now)}, season is $season, scoring games from $start to $end")
    }
}
