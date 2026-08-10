package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/** Why a draw could not pair someone. Stored as its name, and read by nobody — see [LeagueExemption.reason]. */
enum class ExemptionReason {
    /** The active roster was odd and this player was the one left over. */
    ODD,

    /** Every other active player was of their own house, and the draw never pairs two members of the same one. */
    NO_RIVAL
}

/**
 * A session a player was in the running for and the draw could not pair them in.
 *
 * This is the only thing that makes the perfect-attendance bonus computable. A player who is not paired produces no
 * match row, and past membership is not reconstructible — `league_members` keeps one `left_since`, which cannot say
 * whether someone who left and came back twice was active on 15 January. Without this table a session with no match
 * would be indistinguishable from a session where the player was not there, and the bonus would be wrong in the way
 * that shows least: a diligent player losing it without understanding why.
 *
 * Written by the draw, in the same pass as the matches, and **never** by the settlement: an exemption is a decision of
 * the draw, not a consequence of a match not being played. An exemption neutralises a session, it does not credit it —
 * no points come with one.
 */
@GenerateNoArgConstructor
data class LeagueExemption(
    val season: String,
    val session: Int,
    val discordId: String,
    /**
     * The [ExemptionReason] name. Kept as the raw string, because **no code reads this column**: it exists so that
     * "why was I not drawn?" can be answered by a SELECT in May about a draw from January, rather than by rereading
     * logs. Typing it here would suggest someone branches on it.
     */
    val reason: String,
    val created: Date? = null
)
