package com.fulgurogo.api.db.model

import com.fulgurogo.league.db.model.LeagueStanding

/**
 * Who a player is, for the places that name one without ranking them: the two sides of a match, an exemption, an
 * opponent on a profile.
 *
 * The identity half of [ApiLeagueStanding], same field names, so the site renders a player the same way wherever it
 * meets one.
 *
 * [house] is null for a player who has since left theirs — which also makes them inactive, but their matches and the
 * renown they earned stay on display, so the block has to survive it.
 */
data class ApiLeagueMember(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val house: ApiHouseCrest? = null,
    val active: Boolean = true
) {
    companion object {
        fun from(standing: LeagueStanding, crest: ApiHouseCrest?): ApiLeagueMember = ApiLeagueMember(
            discordId = standing.discordId,
            discordName = standing.discordName,
            discordAvatar = standing.discordAvatar,
            house = crest,
            active = standing.active
        )

        /**
         * For a Discord id the standings do not hold. It happens: `CleanService` purges the academy row of a player who
         * left the server but never their matches, so a past pairing can name somebody the season no longer knows.
         * Answering with the bare id keeps the match displayable instead of dropping it.
         */
        fun unknown(discordId: String): ApiLeagueMember = ApiLeagueMember(discordId = discordId, active = false)
    }
}
