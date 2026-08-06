package com.fulgurogo.house

import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.house.HouseModule.TAG
import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.house.db.model.HouseMember
import com.fulgurogo.house.db.model.ranked
import java.time.ZonedDateTime

/**
 * The two switches of the year: closing the season that has just ended, and opening the next one.
 *
 * Both are once-a-year events on a service that ticks every ten minutes, so neither can be driven by the calendar alone
 * — the calendar says "it is September", not "September has been dealt with". `house_seasons` is what says the second,
 * and every branch here claims its row before doing anything a second run would repeat.
 *
 * Ten minutes is plenty for events dated to the day: the worst case is a season opening ten minutes late on 1 September
 * at midnight, which nobody is awake for. It is also the cadence `ping` and `clean` already use.
 */
class HouseSeasonService : PeriodicFlowService(INITIAL_DELAY_IN_SECONDS, INTERVAL_IN_SECONDS) {
    /**
     * One instant for the whole tick, and one branch per period.
     *
     * The two branches are mutually exclusive because [HouseSeason.seasonName] answers differently either side of the
     * break: during the break it names the season that has just ended, which is the one to close, and during the season
     * it names the one to open. Asking the clock twice could straddle midnight on 1 September and close a season while
     * opening it.
     */
    override suspend fun onTick() {
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)

        when (HouseSeason.period(now)) {
            HousePeriod.VACATION -> closeSeason(season)
            HousePeriod.SEASON -> openSeason(season)
        }
    }

    /**
     * Ends a season: reads its final standings, announces the recap, records the closure.
     *
     * The read is what makes the season "frozen" — nothing has to be copied anywhere, since every register row carries
     * its own season and the points of a closed season can never change again. `CleanService` deletes games, not points.
     *
     * A season with no `opened` is skipped, and that is the first-deployment guard: deployed in July 2026,
     * [HouseSeason.seasonName] answers `2025-2026`, a season that never ran and has no row. Without the guard the bot
     * would announce the closing of a season with no members and no points.
     *
     * The order — announce, then record — is deliberate, and it trades a duplicate for a silence. A crash between the two
     * re-announces the recap on the next tick; recording first would instead burn the guard and lose the recap for good.
     * Duplicates are visible and can be deleted, a missing end-of-season message is noticed a year later.
     */
    private fun closeSeason(season: String) {
        val state = HouseDatabaseAccessor.seasonState(season)
        if (state == null || state.opened == null || state.closed != null) return

        val standings = HouseDatabaseAccessor.standings(season).ranked()
        log(TAG, "Closing season $season: " + standings.joinToString { "${it.house.slug} ${it.totalPoints}" })

        // TODO Announce the end-of-season recap on Discord (step 10)

        if (HouseDatabaseAccessor.closeSeason(season)) log(TAG, "Season $season closed")
    }

    /**
     * Starts a season: applies the holiday intentions, clears what is left of them, records the opening.
     *
     * The opening is claimed *last*, which is what makes an interrupted opening safe rather than half-done. Each
     * intention clears itself as it is applied — a `CHANGE` in one statement, a `LEAVE` by deleting the row — so a tick
     * that dies halfway through leaves every member either fully dealt with or still carrying their intention, and the
     * next tick picks up exactly the remainder. Claiming the opening first would strand them: the guard would be spent
     * and nobody would ever be moved.
     *
     * Points are not cleared, here or anywhere. Every register row carries its season and every read filters on it, so
     * last year's ladder survives as history for nothing more than the cost of the column.
     */
    private fun openSeason(season: String) {
        val state = HouseDatabaseAccessor.seasonState(season)
        if (state?.opened != null) return

        HouseDatabaseAccessor.pendingMembers().forEach { applyIntention(it) }

        // Everything left is a STAY, which means nothing to do beyond forgetting it was ever said.
        HouseDatabaseAccessor.clearPendingActions()

        if (HouseDatabaseAccessor.openSeason(season)) log(TAG, "Season $season opened")
    }

    /**
     * One member's holiday intention.
     *
     * An unreadable `pending_action` is left alone rather than guessed at: [HouseAction.from] answers null, this logs and
     * returns, and the bulk clear at the end of the opening removes it. Treating it as a `STAY` would reach the same
     * place, but silently, and a value the column should not hold is worth one line in the log.
     */
    private fun applyIntention(member: HouseMember) {
        when (HouseAction.from(member.pendingAction)) {
            HouseAction.STAY, null -> log(TAG, "${member.discordId} stays, intention was [${member.pendingAction}]")

            HouseAction.LEAVE -> {
                HouseDatabaseAccessor.removeMember(member.discordId)
                log(TAG, "${member.discordId} left house ${member.houseId}")
            }

            HouseAction.CHANGE -> {
                // Drawn among the *other* three, and among the emptiest of those — the same draw a join makes, from the
                // same place, so the two cannot diverge. Counts are read afresh per member, so moving one player is
                // taken into account when drawing for the next.
                val house = HouseAssignment.draw(excluding = member.houseId)
                if (house == null) {
                    // Only reachable with an unseeded `houses` table. Leaving the intention in place is the useful
                    // failure: the member keeps their house and the next tick tries again.
                    log(TAG, "${member.discordId} wanted a change but there was no house to draw from")
                    return
                }

                HouseDatabaseAccessor.changeHouse(member.discordId, house.id)
                log(TAG, "${member.discordId} changed to ${house.slug}")
                // TODO Announce the arrival on Discord (step 10, the same announcement a join makes)
            }
        }
    }

    companion object {
        /** After the scanner's own start, so the two do not open their first connections at the same moment. */
        private const val INITIAL_DELAY_IN_SECONDS = 120L

        /** Ten minutes, as `ping` and `clean` use: these are events dated to the day, not to the second. */
        private const val INTERVAL_IN_SECONDS = 600L
    }
}
