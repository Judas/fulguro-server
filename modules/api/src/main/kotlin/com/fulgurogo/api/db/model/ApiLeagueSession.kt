package com.fulgurogo.api.db.model

import com.fulgurogo.league.Session
import com.fulgurogo.league.db.model.LeagueSessionState
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One session's identity and where it stands.
 *
 * [end] is **exclusive**: a session running "1 to 14" ends on the 15th at 00:00. The site needs to know that to label a
 * fortnight without printing a day that belongs to the next one.
 *
 * [drawn] and [settled] come from `league_sessions`, and both are false for a session with no row — which is the honest
 * answer, since a session that was never drawn has no row at all.
 *
 * The two instants are ISO with an offset, unlike the display strings the games routes serve: these are read by code —
 * to know whether a session is over — not printed as they are.
 */
data class ApiLeagueSession(
    val number: Int,
    /**
     * A ready-made French label for the fortnight, `15 – 30 septembre`.
     *
     * Served rather than left to the site, and not out of convenience: formatting a French date needs a pinned locale, and
     * getting that wrong is silent — this module already shipped a `Thursday 30 April` into a French sentence once, because
     * the JVM default here is `en_US`. A browser has the same trap with its own default. The server owns the wording of
     * everything players read, so it owns this too.
     *
     * A session never spans two months — it is either the 1st to the 14th or the 15th to the end — so one month name is
     * always enough, and the day shown is the **last day included**, [end] being exclusive.
     */
    val label: String,
    val start: String,
    val end: String,
    val drawn: Boolean,
    val settled: Boolean
) {
    companion object {
        /** `2027-01-15T00:00:00+01:00`. Seconds are explicit so the shape does not change when they are zero. */
        private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

        /** `septembre`. [Locale.FRENCH] pinned, never the JVM default — see [label]. */
        private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)

        fun from(session: Session, state: LeagueSessionState?): ApiLeagueSession {
            val lastDay = session.end.minusDays(1)

            return ApiLeagueSession(
                number = session.number,
                label = "${session.start.dayOfMonth} – ${lastDay.dayOfMonth} ${MONTH.format(lastDay)}",
                start = ISO.format(session.start),
                end = ISO.format(session.end),
                drawn = state?.drawn != null,
                settled = state?.settled != null
            )
        }
    }
}
