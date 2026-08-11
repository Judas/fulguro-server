package com.fulgurogo.league

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.house.HouseSeason
import com.fulgurogo.league.LeagueModule.TAG
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
    private const val SESSION_OVERRIDE_KEY = "league.session.override"

    /** The day the second session of a month starts, and the exclusive end of its first one. */
    private const val SECOND_HALF_DAY = 15

    /**
     * A dev-only override: the number of the session to pretend is running, whatever the date.
     *
     * It exists for one reason, and it is a real one. The draw is the module's most consequential write and it must work
     * on 15 September at 07:00 — yet it is **unreachable** outside a session, so between 1 June and 14 September no test
     * can touch it, in dev or anywhere. Shipping it never having run once is the larger risk.
     *
     * ⚠ It forces **two** things, and conflating them is deliberate rather than sloppy: the session in progress, and the
     * morning window. Forcing only the first still leaves the draw untestable except between 07:00 and 09:59. The key
     * therefore means "act as though session N were running and it were the moment to draw".
     *
     * Read through [Config.getOrNull] and logged when set, the same contract as `house.period.override` — **empty in
     * production**, where a value would freeze the league on one session for good.
     *
     * ⚠ And it is not free of consequence at OGS: a draw under this key creates permanent matches, `DELETE` answering 405,
     * so it consumes the `league_match_id` of that (season, session) for the pairing it drew. Reusing the same slot later
     * with a different pairing answers 400 naming the field, which is loud rather than silent — but a slot a real season
     * will want must not be spent on a test.
     */
    private val sessionOverride: Int? = readSessionOverride()

    /** Whether the calendar is being forced, which the service also reads to bypass the morning window. */
    fun isOverridden(): Boolean = sessionOverride != null

    /** One line at startup, and only when the key is set. Its absence in dev is itself the signal. */
    fun logState() {
        sessionOverride?.let {
            log(TAG, "$SESSION_OVERRIDE_KEY is set: session $it is forced current and the draw window is always open")
        }
    }

    private fun readSessionOverride(): Int? {
        val value = Config.getOrNull(SESSION_OVERRIDE_KEY)?.trim()
        if (value.isNullOrEmpty()) return null

        val number = value.toIntOrNull()
        if (number == null) log(TAG, "Ignoring unreadable $SESSION_OVERRIDE_KEY value [$value]")
        return number
    }

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
    fun current(season: String, now: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)): Session? {
        val sessions = sessions(season)
        // The override answers before the calendar, and answers null on a number that is not a session of this season
        // rather than inventing one — a typo in the key must leave the league idle, not pair people into a phantom slot.
        sessionOverride?.let { forced -> return sessions.firstOrNull { it.number == forced } }
        return sessions.firstOrNull { it.contains(now) }
    }

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
