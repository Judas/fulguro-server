package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/admin/league/adjudicate`.
 *
 * A match is named the way the site already sees it — its session and its black player, which is the table's key within a
 * season — rather than by `league_match_id`, which no route serves.
 *
 * The two awards are `LeagueAward` names. Both null withdraws the ruling; exactly one null is a malformed request, since
 * a ruling for one side only would leave the other side's count to a `result` the ruling was meant to overrule.
 *
 * Nullable throughout for the reason [LinkRequestBody] gives: Gson does not honour Kotlin nullability.
 */
data class LeagueAdjudicationRequestBody(
    val session: Int?,
    val blackDiscordId: String?,
    val blackAward: String?,
    val whiteAward: String?
)
