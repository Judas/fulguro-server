package com.fulgurogo.api.db.model

import com.fulgurogo.league.db.model.LeagueExemption

/**
 * A player the draw could not pair, and why.
 *
 * They belong in a session's response, apart from the matches, and leaving them out would be a real omission: the page
 * would look as though an active member had been forgotten, when the draw explicitly established there was nobody for
 * them. [reason] is `ODD` — an odd headcount — or `NO_RIVAL` — everyone left was of their own house.
 *
 * An exemption is worth no points. It only keeps the perfect-attendance bonus reachable.
 */
data class ApiLeagueExemption(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val house: ApiLeagueCrest? = null,
    val reason: String
) {
    companion object {
        fun from(exemption: LeagueExemption, member: ApiLeagueMember): ApiLeagueExemption = ApiLeagueExemption(
            discordId = member.discordId,
            discordName = member.discordName,
            discordAvatar = member.discordAvatar,
            house = member.house,
            reason = exemption.reason
        )
    }
}
