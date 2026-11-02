package com.fulgurogo.api.admin

import com.fulgurogo.api.ApiModule.TAG
import com.fulgurogo.api.db.model.ApiHouseAdjustment
import com.fulgurogo.common.logger.log
import com.fulgurogo.house.HouseNotifier
import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.HouseSeason
import com.fulgurogo.house.db.HouseDatabaseAccessor

/** What an adjustment request came to. */
sealed interface HouseAdjustmentResult {
    /** The player is in no house, so there is nowhere to put the points. */
    data object NotMember : HouseAdjustmentResult

    /** The summer break: the season is closed and its totals already announced. */
    data object OutOfSeason : HouseAdjustmentResult

    data class Done(val adjustment: ApiHouseAdjustment) : HouseAdjustmentResult
}

/**
 * Gives or takes house points by hand, for what the scanner cannot see: an event, a tournament, a penalty.
 *
 * An interface for the reason [LeagueAdministrator] is one: it is the seam the route's tests are written against, so the
 * validation and the answers can be exercised without a database or a Discord bot.
 */
interface HouseAdministrator {
    fun adjust(discordId: String, points: Int, reason: String, adminDiscordId: String): HouseAdjustmentResult
}

class HouseAdministrationService : HouseAdministrator {
    override fun adjust(discordId: String, points: Int, reason: String, adminDiscordId: String): HouseAdjustmentResult {
        // During the break `seasonName` still names the season that just ended, which has been closed and recapped:
        // points written there would move a result already announced.
        if (HouseSeason.period() != HousePeriod.SEASON) return HouseAdjustmentResult.OutOfSeason

        val member = HouseDatabaseAccessor.member(discordId) ?: return HouseAdjustmentResult.NotMember
        val house = HouseDatabaseAccessor.house(member.houseId) ?: return HouseAdjustmentResult.NotMember
        val season = HouseSeason.seasonName()

        HouseDatabaseAccessor.addAdjustment(discordId, house.id, season, points, reason, adminDiscordId)

        // Best-effort, after the write: the database is the record, and a Discord hiccup must not fail a grant that
        // has already counted.
        try {
            HouseNotifier.notifyAdjustment(discordId, house, points, reason)
        } catch (e: Exception) {
            log(TAG, "notifyAdjustment FAILURE for $discordId: ${e.message}", e)
        }

        val total = HouseDatabaseAccessor.playerStanding(season, discordId)?.standing?.total ?: points
        return HouseAdjustmentResult.Done(
            ApiHouseAdjustment(
                discordId = discordId,
                house = house.slug,
                season = season,
                points = points,
                reason = reason,
                total = total
            )
        )
    }
}
