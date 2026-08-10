package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/**
 * Where one session stands: the row of `league_sessions`, and the three guards that make each event of its life happen
 * once.
 *
 * The row is created by the draw, so **a session that has not been drawn has no row at all**. That is the right state
 * for a session still to come, and a readable difference from a session drawn with no match in it — which has a row
 * with [drawn] set.
 */
@GenerateNoArgConstructor
data class LeagueSessionState(
    val season: String,
    val session: Int,
    /**
     * When the pairings were drawn. This, and not the calendar, is what says the draw has happened: a service ticking
     * every ten minutes sees the start of a session about 1400 times.
     *
     * It is also restamped by the daily redraw of an empty session, which is why the redraw compares it against the
     * start of today rather than against null.
     */
    val drawn: Date? = null,
    /**
     * When the draw was announced. Separate from [drawn] so that a Discord failure costs neither a second draw nor the
     * announcement.
     */
    val notified: Date? = null,
    /**
     * When the session was settled: the moment its unfinished matches became permanently void. Not an operation to run
     * twice, and the only one in the module that closes a door.
     */
    val settled: Date? = null
)
