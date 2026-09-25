package com.fulgurogo.league.db.model

/**
 * What an administrator decided one player gets out of a match, whatever OGS or the settlement said. Stored by [name] in
 * `league_matches.black_award` / `white_award`.
 *
 * Each value is defined by what it counts as rather than by a number of points, because that is what the standings
 * already know how to total: [LeagueRenown.of] takes played, won and exempted counts, and each award lands in exactly one
 * of them (or none).
 *
 * - [FORFEIT]: nothing, and the session is lost for the perfect-attendance bonus — what `unplayed` does to both sides.
 * - [EXEMPT]: nothing, but the session counts as exempted, so the bonus is kept — what the draw's bench does.
 * - [PARTICIPANT]: counts as played, 2 renown.
 * - [WINNER]: counts as played and won, 7 renown. At most one per match.
 */
enum class LeagueAward {
    FORFEIT, EXEMPT, PARTICIPANT, WINNER;

    companion object {
        /** The award spelled [value], or null for anything else — including the case, which is the API's to get right. */
        fun of(value: String?): LeagueAward? = entries.firstOrNull { it.name == value }
    }
}
