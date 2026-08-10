package com.fulgurogo.league.db.model

/**
 * One line of the league standings. Composed in Kotlin, not mapped from a row — sql2o cannot fill a nested object, and
 * [renown] is one.
 *
 * [houseId] is the player's house **now**, not the one frozen on their matches. The two can differ, and each is right
 * for its own question: a standings line says where the player currently stands, while an academy's total sums the
 * frozen ids, which is what stops it shrinking when someone leaves. A player who has left their house shows null here,
 * and is inactive by the same token.
 *
 * [played] + [won] + [lost] do not reconcile with the number of sessions: a match still open or voided by the settlement
 * counts in none of them, and [exempted] counts sessions rather than matches.
 *
 * [rank] is stamped in Kotlin after sorting on [LeagueRenown.total], never by an ORDER BY, so the order shown, the total
 * shown and the rank shown cannot disagree. Competition ranking: equal totals share a rank and the next one skips
 * (1, 2, 2, 4).
 */
data class LeagueStanding(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val houseId: Int? = null,
    /** False for a player who left, or whom the reconciliation dropped. Their matches and renown stay in the table. */
    val active: Boolean = true,
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    /** Sessions the draw could not pair them in. Worth no points, but they keep the perfect-attendance bonus reachable. */
    val exempted: Int = 0,
    val renown: LeagueRenown,
    val rank: Int = 0
)
