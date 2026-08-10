package com.fulgurogo.league.db.model

/**
 * A player's renown, broken down. Composed in Kotlin, never mapped from a row.
 *
 * It is broken down rather than reduced to a single number so that the website prints the detail without re-adding
 * anything: the total it shows and the rank it shows come from [total], which exists once.
 *
 * A player who plays and wins all 16 sessions tops out at 16 × 7 + 10 = **122**.
 */
data class LeagueRenown(
    val playedPoints: Int,
    val victoryPoints: Int,
    val perfectBonus: Int
) {
    fun total(): Int = playedPoints + victoryPoints + perfectBonus

    companion object {
        /** Per match played: the game was started before the end of its session and finished by the settlement. */
        const val POINTS_PER_PLAYED = 2

        /** Per victory, on top of the two above. */
        const val POINTS_PER_VICTORY = 5

        /** Once, for a season where every session was either played or exempted. */
        const val PERFECT_BONUS = 10

        /**
         * The renown of one player from their counts.
         *
         * The bonus is read **session by session, not match by match**, which is the whole reason [exempted] is a
         * parameter: a session the draw could not pair the player in is not their fault, so it neutralises rather than
         * penalising — while crediting nothing. Everything else breaks the bonus, including a session where the player
         * was not an active member, because that is their own choice.
         *
         * [sessionCount] comes from the calendar rather than a constant 16, so a change to the split cannot leave the
         * bonus unreachable or free.
         *
         * `>=` rather than `==` on purpose. The two are equivalent while the invariants hold — at most one match and at
         * most one exemption per player per session, and never both — but if one ever broke, `==` would quietly deny the
         * bonus to the players who deserved it most, which is the error that would take longest to notice.
         */
        fun of(played: Int, won: Int, exempted: Int, sessionCount: Int): LeagueRenown = LeagueRenown(
            playedPoints = played * POINTS_PER_PLAYED,
            victoryPoints = won * POINTS_PER_VICTORY,
            perfectBonus = if (played + exempted >= sessionCount) PERFECT_BONUS else 0
        )
    }
}
