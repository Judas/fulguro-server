package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * What one academy has earned this season, counted over the **house frozen on each match** rather than over who is in it
 * now.
 *
 * That is what keeps an academy's renown from shrinking when a player leaves it — the same property `house_points` has,
 * and for the same reason. A player who changed house mid-season leaves their earlier matches credited where they were
 * played.
 *
 * ⚠ **The perfect-attendance bonus is not in [renown], and the plan does not say where it should go.** It is a
 * season-long personal achievement rather than something earned in a match, so attributing it to an academy would mean
 * picking one house for a player who may have worn two. The consequence to know: an academy's renown is **not** the sum
 * of its members' renown, and the two figures are answers to different questions.
 */
@GenerateNoArgConstructor
data class LeagueAcademyStanding(
    val houseId: Int,
    val played: Int = 0,
    val won: Int = 0
) {
    /** Match points only: the two per game played and the five per win, by the scale the players are scored on. */
    fun renown(): Int = played * LeagueRenown.POINTS_PER_PLAYED + won * LeagueRenown.POINTS_PER_VICTORY
}

/**
 * How many rows a draw actually created, one count per table.
 *
 * A pair rather than two return values because `writeDraw` writes both in one transaction, and the caller logs both — the
 * two numbers only mean anything together.
 */
data class DrawWritten(val matches: Int, val exemptions: Int)
