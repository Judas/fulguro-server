package com.fulgurogo.api.db.model

import com.fulgurogo.league.Session
import com.fulgurogo.league.db.model.LeagueSessionState
import java.time.format.DateTimeFormatter

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
    val start: String,
    val end: String,
    val drawn: Boolean,
    val settled: Boolean
) {
    companion object {
        /** `2027-01-15T00:00:00+01:00`. Seconds are explicit so the shape does not change when they are zero. */
        private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

        fun from(session: Session, state: LeagueSessionState?): ApiLeagueSession = ApiLeagueSession(
            number = session.number,
            start = ISO.format(session.start),
            end = ISO.format(session.end),
            drawn = state?.drawn != null,
            settled = state?.settled != null
        )
    }
}
