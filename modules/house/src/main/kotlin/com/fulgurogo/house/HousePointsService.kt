package com.fulgurogo.house

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.house.HouseModule.TAG
import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.house.db.model.HouseGame
import com.fulgurogo.house.db.model.HouseMember
import com.fulgurogo.house.db.model.HousePoints
import java.time.ZonedDateTime

/**
 * The scanner: walks the games nobody has scored yet and fills the points register.
 *
 * [PeriodicFlowService] and not `StalestFirstService` — there is no queue of stale rows to rotate here, only games that
 * have never been scored, and the register's primary key is what remembers which. It reads its own progress from the
 * database on every tick, so it needs no state of its own and a restart costs nothing.
 *
 * The period is deliberately *not* consulted. A season's window is a window over the date of the game, so the scanner
 * keeps running through June, July and August, where it still picks up the last games of May — which have not been
 * deleted yet — while a game played in June falls outside the window and can never be scored. Stopping on `VACATION`
 * would silently drop the end of every season.
 */
class HousePointsService : PeriodicFlowService(INITIAL_DELAY_IN_SECONDS, INTERVAL_IN_SECONDS) {
    override suspend fun onTick() {
        // One instant for the whole tick, so the season a game is filed under cannot differ from the window it was
        // selected by, however unlucky the timing on 1 September.
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)
        val (start, end) = HouseSeason.seasonWindow(season)

        val games = HouseDatabaseAccessor.gamesToScore(start.toDate(), end.toDate(), BATCH_SIZE)
        if (games.isEmpty()) return

        // Both sides of every game in one round trip, rather than two queries per game.
        val discordIds = games.flatMap { listOfNotNull(it.blackDiscordId, it.whiteDiscordId) }.toSet()
        val members = HouseDatabaseAccessor.members(discordIds)

        val points = games.flatMap { score(it, season, members) }
        val written = HouseDatabaseAccessor.addPoints(points)
        log(TAG, "Scored ${games.size} game(s) for season $season, ${points.size} row(s), $written new")
    }

    /** The nought to two register rows a game is worth, logged one line per game so a first run can be read by hand. */
    private fun score(game: HouseGame, season: String, members: Map<String, HouseMember>): List<HousePoints> {
        val black = game.blackDiscordId?.let { members[it] }
        val white = game.whiteDiscordId?.let { members[it] }

        val points = listOfNotNull(
            pointsFor(game, true, season, black, white),
            pointsFor(game, false, season, white, black)
        )

        // Every game the selection returns has a member who had joined by the time it was played, so it always scores
        // at least one row. If it does not, the selection and the scale disagree and that game will come back on every
        // tick from now on, taking a slot in the batch with it.
        if (points.isEmpty()) log(TAG, "Game ${game.goldId} scored nothing, it will be selected again")
        else log(TAG, "Scored ${game.goldId}: " + points.joinToString { "${it.discordId} +${it.total()}" })

        return points
    }

    /**
     * One side of a game, or null when that side earns nothing.
     *
     * [member] is that side's membership, [opponent] the other side's — the scale only needs the opponent's house, and
     * takes it as it stands today. It is not filtered on [HouseMember.joined] the way the player's own side is: the
     * rival bonus asks whether the opponent is in another house, not since when.
     */
    private fun pointsFor(
        game: HouseGame,
        black: Boolean,
        season: String,
        member: HouseMember?,
        opponent: HouseMember?
    ): HousePoints? {
        if (member == null) return null

        // The same guard the selection applies in SQL, repeated here because the selection is per game and this is per
        // player: a game between a September member and a November one comes back for the first and must not score for
        // the second.
        if (game.date.before(member.joined)) return null

        return HousePointsCalculator.fromGame(game, black, season, member.houseId, opponent?.houseId)
    }

    companion object {
        /** Past `GoldService`'s own start, so the two do not open their first connections at the same moment. */
        private const val INITIAL_DELAY_IN_SECONDS = 90L

        /** Off the 15s beat that gold and fgc share, in the spirit of the staggered intervals across the app. */
        private const val INTERVAL_IN_SECONDS = 30L

        /** Big enough to work through a backlog at 100 games a minute, small enough to keep a tick short. */
        private const val BATCH_SIZE = 50
    }
}
