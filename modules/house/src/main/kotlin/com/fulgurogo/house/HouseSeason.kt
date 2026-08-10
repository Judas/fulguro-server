package com.fulgurogo.house

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.house.HouseModule.TAG
import java.time.ZonedDateTime

/** Whether the houses are competing, or on their summer break. */
enum class HousePeriod { SEASON, VACATION }

/**
 * The house calendar. A season runs from 1 September to 31 May and is named after the two years it spans
 * (`"2026-2027"`); June, July and August are the break, when nothing is scored and the holiday choices are applied.
 *
 * Nothing here reads the database or the clock behind the caller's back: [period] and [seasonName] take the instant to
 * answer for, so the four dates worth checking — 31 August, 1 September, 31 May, 1 June — can be checked by hand.
 */
object HouseSeason {
    private const val PERIOD_OVERRIDE_KEY = "house.period.override"

    /** September: the first month of a season, and the inclusive start of its window. */
    private const val SEASON_START_MONTH = 9

    /** June: the first month of the break, and the exclusive end of the season window. */
    private const val VACATION_START_MONTH = 6

    /** August: the last month of the break. */
    private const val VACATION_END_MONTH = 8

    /**
     * Read once at startup, and logged when it is set so that it does not get forgotten on a production config.
     *
     * It only moves the *period*, never the calendar: [seasonName] and [seasonWindow] still answer from the real date.
     * Forcing `SEASON` in June therefore opens the paths that a period gates — joining a house, above all — without
     * making June games scorable, since they stay outside the window of the season that just ended.
     */
    private val periodOverride: HousePeriod? = readPeriodOverride()

    fun period(now: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)): HousePeriod = periodOverride
        ?: if (now.monthValue in VACATION_START_MONTH..VACATION_END_MONTH) HousePeriod.VACATION
        else HousePeriod.SEASON

    /**
     * The season [now] belongs to. From September on, that is the season starting this year; up to May, the one that
     * started last year.
     *
     * In June, July and August it names the season that has just ended, and the single `year - 1` branch it shares with
     * January-to-May is not a coincidence. A game played on 31 May and only scanned on 1 June still falls inside that
     * season's window and still scores, while a game played on 15 June never will. "No points outside the season" is a
     * rule about the date of the *game*, not the date of the scan, and this is what keeps the last games of May.
     */
    fun seasonName(now: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)): String {
        val startYear = if (now.monthValue >= SEASON_START_MONTH) now.year else now.year - 1
        return "$startYear-${startYear + 1}"
    }

    /**
     * The half-open window of a season: 1 September 00:00 inclusive, to 1 June 00:00 exclusive. A game counts when
     * `start <= date < end`, which puts the whole of 31 May in and the whole of June out.
     *
     * The end is derived from the first year rather than read from the second, so a hand-written `"2026-2028"` cannot
     * produce a two-year window.
     */
    fun seasonWindow(season: String): Pair<ZonedDateTime, ZonedDateTime> {
        val startYear = season.substringBefore('-').toInt()
        return startOfMonth(startYear, SEASON_START_MONTH) to startOfMonth(startYear + 1, VACATION_START_MONTH)
    }

    private fun startOfMonth(year: Int, month: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, DATE_ZONE)

    private fun readPeriodOverride(): HousePeriod? {
        val value = Config.getOrNull(PERIOD_OVERRIDE_KEY)?.trim()
        if (value.isNullOrEmpty()) return null

        val override = HousePeriod.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        if (override == null) log(TAG, "Ignoring unknown $PERIOD_OVERRIDE_KEY value [$value]")
        else log(TAG, "$PERIOD_OVERRIDE_KEY is set: the period is forced to $override")
        return override
    }
}
