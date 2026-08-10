package com.fulgurogo.league

import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.house.HouseSeason
import java.time.ZonedDateTime

/**
 * One session of the league: a fortnight, and the window a paired match has to be started in.
 *
 * [end] is **exclusive**, like [HouseSeason.seasonWindow]: a session running "1 to 14" ends on the 15th at 00:00, so
 * the whole of the 14th is in and none of the 15th is.
 */
data class Session(val number: Int, val start: ZonedDateTime, val end: ZonedDateTime) {
    fun contains(instant: ZonedDateTime): Boolean = !instant.isBefore(start) && instant.isBefore(end)

    fun hasEnded(instant: ZonedDateTime): Boolean = !instant.isBefore(end)
}

/**
 * The league calendar: 16 sessions over the house season, two per month — the 1st to the 14th, then the 15th to the end
 * of the month, whether that month has 28, 30 or 31 days.
 *
 * Two fortnights of the nine months are not sessions: the first half of September, when the academies are being formed
 * and nobody is paired yet, and the second half of December. 9 × 2 − 2 = 16.
 *
 * The season is the houses' one, read from [HouseSeason] rather than redefined here, and nothing reads the clock behind
 * the caller's back: [current] and [ended] take the instant to answer for, so the boundaries worth checking by hand —
 * 14 and 15 September, 14 and 20 December, 1 January, 31 May, 1 June — can be checked one call at a time.
 */
object LeagueSession {
    /** The day the second session of a month starts, and the exclusive end of its first one. */
    private const val SECOND_HALF_DAY = 15

    /**
     * The two fortnights of the season that are not sessions, as `month to half` with the half numbered 1 or 2:
     * September's first — academy formation — and December's second — the holidays.
     */
    private val SKIPPED_HALVES: Set<Pair<Int, Int>> = setOf(9 to 1, 12 to 2)

    /**
     * The sessions of [season], in order, numbered 1 to [count].
     *
     * Built by walking the months of the house season and splitting each in two, so the count follows the season window
     * instead of being asserted: shortening the season cannot leave a 16 behind that no longer matches the sessions.
     */
    fun sessions(season: String): List<Session> {
        val (seasonStart, seasonEnd) = HouseSeason.seasonWindow(season)
        val halves = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()

        var month = seasonStart
        while (month.isBefore(seasonEnd)) {
            val nextMonth = month.plusMonths(1)
            val middle = month.withDayOfMonth(SECOND_HALF_DAY)

            if ((month.monthValue to 1) !in SKIPPED_HALVES) halves += month to middle
            if ((month.monthValue to 2) !in SKIPPED_HALVES) halves += middle to nextMonth

            month = nextMonth
        }

        return halves.mapIndexed { index, (start, end) -> Session(index + 1, start, end) }
    }

    /**
     * The session [now] falls in, or null.
     *
     * Null outside the season, and also inside the two skipped fortnights: a missing session is a hole in the calendar,
     * not a longer neighbour. Extending session 6 to 1 January would make the holiday games count for it, and — since a
     * match is settled at the end of its session — would let those two matches live a fortnight longer than every other.
     */
    fun current(season: String, now: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)): Session? =
        sessions(season).firstOrNull { it.contains(now) }

    /** The sessions of [season] that are over, for the settlement to walk. */
    fun ended(season: String, now: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)): List<Session> =
        sessions(season).filter { it.hasEnded(now) }

    /**
     * How many sessions [season] has: 16.
     *
     * Counted rather than written down, because the perfect-attendance bonus compares a player's played-or-exempted
     * sessions against it. A hardcoded 16 that stopped matching the split would make the bonus unreachable, or free.
     */
    fun count(season: String): Int = sessions(season).size
}
