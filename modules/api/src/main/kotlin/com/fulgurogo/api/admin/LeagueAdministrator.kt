package com.fulgurogo.api.admin

import com.fulgurogo.api.db.model.ApiLeagueMatch
import com.fulgurogo.api.league.LeagueApiComposer
import com.fulgurogo.house.HouseSeason
import com.fulgurogo.league.db.LeagueDatabaseAccessor
import com.fulgurogo.league.db.model.LeagueAward

/**
 * The league's two administrator actions, on the current season — the only one the API serves.
 *
 * An interface for the reason [PlayerPurger] is a `fun interface`: it is the seam the routes' tests are written against,
 * so the validation and the answers can be exercised without a database.
 */
interface LeagueAdministrator {
    /** Deactivates [discordId], as their own leave would. False when they are not a member of the season. */
    fun remove(discordId: String): Boolean

    /**
     * Rules on the match of [session] whose black player is [blackDiscordId], or withdraws the ruling when [awards] is
     * null. Answers the match as the site now shows it, or null when there is no such match.
     */
    fun adjudicate(
        session: Int,
        blackDiscordId: String,
        awards: Pair<LeagueAward, LeagueAward>?,
        adminDiscordId: String
    ): ApiLeagueMatch?
}

class LeagueAdministrationService : LeagueAdministrator {
    override fun remove(discordId: String): Boolean {
        val season = HouseSeason.seasonName()
        // The UPDATE cannot tell an unknown member from one already inactive, hence the read — see `Api.leaveLeague`.
        if (LeagueDatabaseAccessor.member(season, discordId) == null) return false
        LeagueDatabaseAccessor.setActive(season, discordId, false)
        return true
    }

    override fun adjudicate(
        session: Int,
        blackDiscordId: String,
        awards: Pair<LeagueAward, LeagueAward>?,
        adminDiscordId: String
    ): ApiLeagueMatch? {
        val season = HouseSeason.seasonName()
        val found = if (awards == null) {
            LeagueDatabaseAccessor.clearAdjudication(season, session, blackDiscordId)
        } else {
            LeagueDatabaseAccessor.adjudicate(season, session, blackDiscordId, awards.first, awards.second, adminDiscordId)
        }
        if (!found) return null

        // Read back rather than rebuilt from the request, so the answer is what the standings will count.
        val match = LeagueDatabaseAccessor.match(season, session, blackDiscordId) ?: return null
        return LeagueApiComposer(season).match(match)
    }
}
