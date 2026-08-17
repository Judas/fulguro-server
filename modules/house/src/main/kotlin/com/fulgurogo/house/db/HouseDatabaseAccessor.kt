package com.fulgurogo.house.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.house.db.model.*
import org.sql2o.Connection
import java.util.*

/**
 * Every read the house module makes, plus the writes: the scanner's points, and joining or recording an intention.
 *
 * The aggregates take the season as a parameter, which is why they are queries here and not views: a view cannot be
 * told which season is current, and that is computed in Kotlin.
 *
 * Ranking and totalling are deliberately split between the two languages. SQL groups and sums, Kotlin sorts and ranks.
 * That way the order a caller displays, the total it prints and the rank it shows all come from
 * [HousePointsBreakdown.total] rather than from an ORDER BY that might drift from it.
 */
object HouseDatabaseAccessor {
    private const val HOUSES_TABLE = "houses"
    private const val MEMBERS_TABLE = "house_members"
    private const val POINTS_TABLE = "house_points"
    private const val SEASONS_TABLE = "house_seasons"
    private const val GAMES_VIEW = "house_games"
    private const val DISCORD_TABLE = "discord_user_info"

    /**
     * The seven scoring columns summed into one value, for the one case SQL has to do the totalling itself.
     *
     * This is the second copy of [HousePointsBreakdown.total] and the only one that cannot be avoided: totalling a
     * house has to include the points of players who have left it, so it cannot be folded over a list of current
     * members. Add a column to the scale and both have to change.
     */
    private const val TOTAL_SUM = "played + gold_opponent + rival_house + long_game + victory + even_game + ranked"

    /** The seven columns as SUMs, aliased back to their own names so the auto-derivation still maps them. */
    private const val POINTS_SUMS =
        " COALESCE(SUM(p.played), 0) AS played, " +
                " COALESCE(SUM(p.gold_opponent), 0) AS gold_opponent, " +
                " COALESCE(SUM(p.rival_house), 0) AS rival_house, " +
                " COALESCE(SUM(p.long_game), 0) AS long_game, " +
                " COALESCE(SUM(p.victory), 0) AS victory, " +
                " COALESCE(SUM(p.even_game), 0) AS even_game, " +
                " COALESCE(SUM(p.ranked), 0) AS ranked "

    fun houses(): List<House> = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $HOUSES_TABLE ORDER BY id")
            .throwOnMappingFailure(false)
            .executeAndFetch(House::class.java)
            ?: listOf()
    }

    fun house(slug: String): House? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $HOUSES_TABLE WHERE slug = :slug LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("slug", slug)
            .executeAndFetchFirst(House::class.java)
    }

    /**
     * One house by internal id, or null when no house has it.
     *
     * The id-keyed counterpart of [house], for the callers that hold a `house_members` row rather than a slug — the
     * season transition needs the house a member is leaving, and only its id is written down.
     */
    fun house(id: Int): House? = DatabaseAccessor.withDao { connection -> house(connection, id) }

    /** The player's membership, or null when they are in no house. */
    fun member(discordId: String): HouseMember? = DatabaseAccessor.withDao { connection ->
        member(connection, discordId)
    }

    /**
     * The memberships of [discordIds], keyed by Discord id and missing the ids that are in no house.
     *
     * The scanner's way of not asking [member] twice per game: one round trip for a whole batch, and the misses are
     * answered by the map rather than by a query returning nothing.
     */
    fun members(discordIds: Collection<String>): Map<String, HouseMember> {
        // `IN ()` is a syntax error in MySQL, and an empty batch is the normal quiet case, not an exceptional one.
        if (discordIds.isEmpty()) return mapOf()

        return DatabaseAccessor.withDao { connection ->
            val query = "SELECT * FROM $MEMBERS_TABLE WHERE discord_id IN (:discordIds)"
            connection
                .query(query)
                .throwOnMappingFailure(false)
                .addParameter("discordIds", discordIds)
                .executeAndFetch(HouseMember::class.java)
                ?.associateBy { it.discordId }
                ?: mapOf()
        }
    }

    /**
     * The four houses with their size, their season total and their best current member.
     *
     * Three queries on one connection: the houses, the aggregates, and every ranked member at once — grouped per house
     * in Kotlin rather than asked for four times over.
     */
    fun standings(season: String): List<HouseStanding> = DatabaseAccessor.withDao { connection ->
        val houses = connection
            .query("SELECT * FROM $HOUSES_TABLE ORDER BY id")
            .throwOnMappingFailure(false)
            .executeAndFetch(House::class.java)
            ?: listOf()

        val totals = houseTotals(connection, season).associateBy { it.houseId }
        val leaders = rankedMembers(connection, season, null)
            .groupBy { it.houseId }
            .mapValues { (_, members) -> members.withRanks().firstOrNull() }

        houses.map { house ->
            HouseStanding(
                house = house,
                memberCount = totals[house.id]?.memberCount ?: 0,
                totalPoints = totals[house.id]?.totalPoints ?: 0,
                leader = leaders[house.id]
            )
        }
    }

    /** The current members of one house, best first, each with their rank. */
    fun ranking(season: String, houseId: Int): List<HouseRankedMember> = DatabaseAccessor.withDao { connection ->
        rankedMembers(connection, season, houseId).withRanks()
    }

    /**
     * One house by slug with its figures and its full ranking, or null when no house has that slug.
     *
     * The three reads share a connection so that the total, the size and the ranking are all read at the same instant,
     * and the leader is taken from the ranking rather than asked for again — see [HouseDetails].
     *
     * [houseTotals] is asked for all four houses and filtered here rather than given a `WHERE`: it is four rows, and one
     * aggregate query beats two variants of it that have to keep agreeing on how a house is totalled.
     */
    fun details(season: String, slug: String): HouseDetails? = DatabaseAccessor.withDao { connection ->
        val house = connection
            .query("SELECT * FROM $HOUSES_TABLE WHERE slug = :slug LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("slug", slug)
            .executeAndFetchFirst(House::class.java)
            ?: return@withDao null

        val totals = houseTotals(connection, season).firstOrNull { it.houseId == house.id }
        val members = rankedMembers(connection, season, house.id).withRanks()

        HouseDetails(
            standing = HouseStanding(
                house = house,
                memberCount = totals?.memberCount ?: 0,
                totalPoints = totals?.totalPoints ?: 0,
                leader = members.firstOrNull()
            ),
            members = members
        )
    }

    /**
     * The house block of a player's profile: their house, their line in its ranking, their pending intention. Null when
     * they are in no house.
     *
     * The player's points are read as their row of the house's own ranking rather than summed on their own, so the total
     * a profile prints is by construction the total its rank came from. Ranking the whole house to keep one line of it is
     * the same trade [ranking] already makes — a house is a few dozen rows, and tie handling exists in exactly one place.
     *
     * Null too when the member has no `discord_user_info` row, since the ranking is an INNER JOIN on it. Unreachable from
     * `GET /gold/api/player/{id}`, whose player came out of `api_players` — a view driven from that very table — and a
     * house block filled with zeroes for a player who has actually scored would be worse than no block at all.
     */
    fun playerStanding(season: String, discordId: String): HousePlayerStanding? =
        DatabaseAccessor.withDao { connection ->
            val member = member(connection, discordId) ?: return@withDao null
            val house = house(connection, member.houseId) ?: return@withDao null
            val standing = rankedMembers(connection, season, member.houseId)
                .withRanks()
                .firstOrNull { it.discordId == discordId }
                ?: return@withDao null

            HousePlayerStanding(
                house = house,
                standing = standing,
                pendingAction = member.pendingAction,
                // Resolved on the same connection as the rest, and null when the column names a house that no longer
                // exists -- a profile showing no target reads better than one failing over an unseeded row.
                pendingHouse = member.pendingHouseId?.let { house(connection, it) }
            )
        }

    /**
     * The next [batchSize] games left to score, oldest first: finished, inside the season window, involving at least
     * one house member who had already joined when they were played, and not yet in the register.
     *
     * The whole of the scanner's bookkeeping is in this one query — no cursor, no "scored" flag, nothing to reset.
     * Three properties hold it up, and each is quiet when broken:
     *
     * - **The join on the members** drops the games no member played. Without it those games would come back every
     *   tick, fill the batch and stall the scan for good once there are [batchSize] of them.
     * - **`g.date >= m.joined`** is what stops back-scoring: a player who joins in November earns nothing on the
     *   October games still inside CleanService's 32-day window.
     * - **The season window** puts June, July and August games permanently out of reach, which is "no points outside
     *   the season" with no extra state. It is a window over the *game* date, so a late-May game scanned in June still
     *   scores — which is why the scanner keeps running through the summer instead of stopping on the period.
     *
     * `DISTINCT` because the join matches twice on a game between two members, and a duplicate would waste a slot in
     * the batch. `p.gold_id IS NULL` marks progress per *game* while eligibility is per *player*: a game A and B
     * played in October, with A a member since September and B since November, credits A only and counts as done.
     * That is the intended behaviour, not an oversight.
     */
    fun gamesToScore(seasonStart: Date, seasonEnd: Date, batchSize: Int): List<HouseGame> =
        DatabaseAccessor.withDao { connection ->
            val query = "SELECT DISTINCT g.* FROM $GAMES_VIEW g " +
                    " JOIN $MEMBERS_TABLE m " +
                    "   ON m.discord_id IN (g.black_discord_id, g.white_discord_id) " +
                    "  AND g.date >= m.joined " +
                    " LEFT JOIN $POINTS_TABLE p ON p.gold_id = g.gold_id " +
                    " WHERE p.gold_id IS NULL " +
                    "   AND g.date >= :seasonStart AND g.date < :seasonEnd " +
                    " ORDER BY g.date " +
                    " LIMIT :batchSize "
            connection
                .query(query)
                .throwOnMappingFailure(false)
                .addParameter("seasonStart", seasonStart)
                .addParameter("seasonEnd", seasonEnd)
                .addParameter("batchSize", batchSize)
                .executeAndFetch(HouseGame::class.java)
                ?: listOf()
        }

    /**
     * Writes the register rows the scanner produced, and answers how many were new.
     *
     * `INSERT IGNORE`, the idiom the platform accessors already use, and not `INSERT ... ON DUPLICATE KEY UPDATE
     * gold_id = gold_id`. Both make a second pass over the same game a no-op, but only this one lets the caller *know*:
     * the app's JDBC url leaves `useAffectedRows` at its default, so the driver reports rows **matched** rather than
     * rows changed, and the duplicate branch of `ON DUPLICATE KEY UPDATE` reports 1 exactly like an insert. Measured on
     * the real database, not deduced. The cost of `INSERT IGNORE` is real and accepted: it also demotes a genuine
     * failure, a value too wide for its column say, to a warning nobody reads.
     *
     * `scored_at` comes from `NOW()`, so the register is stamped by the database clock rather than by a JVM that might
     * be in another zone. Nothing reads it yet: with no anti-farming cap in this delivery, it is what makes one
     * computable after the fact, mid-season, without a migration.
     */
    fun addPoints(points: List<HousePoints>): Int {
        if (points.isEmpty()) return 0

        return DatabaseAccessor.withDao { connection ->
            val sql = "INSERT IGNORE INTO $POINTS_TABLE( " +
                    " gold_id, discord_id, house_id, season, " +
                    " played, gold_opponent, rival_house, long_game, victory, even_game, ranked, scored_at) " +
                    " VALUES (:goldId, :discordId, :houseId, :season, " +
                    " :played, :goldOpponent, :rivalHouse, :longGame, :victory, :evenGame, :ranked, NOW()) "

            var inserted = 0
            points.forEach { row ->
                connection
                    .query(sql)
                    .addParameter("goldId", row.goldId)
                    .addParameter("discordId", row.discordId)
                    .addParameter("houseId", row.houseId)
                    .addParameter("season", row.season)
                    .addParameter("played", row.played)
                    .addParameter("goldOpponent", row.goldOpponent)
                    .addParameter("rivalHouse", row.rivalHouse)
                    .addParameter("longGame", row.longGame)
                    .addParameter("victory", row.victory)
                    .addParameter("evenGame", row.evenGame)
                    .addParameter("ranked", row.ranked)
                    .executeUpdate()

                // 1 on an insert, 0 when the row was already there and IGNORE dropped this one.
                if (connection.result == 1) inserted++
            }
            inserted
        }
    }

    /**
     * Puts a player in a house, stamping `joined` with the database clock, and answers whether this call is the one that
     * did it.
     *
     * A false means the player already had a house — which the API also checks by reading first, but only the primary key
     * can answer it without a race. Two joins landing at once would otherwise both read "no house" and the second would
     * either overwrite the first or blow up on the duplicate key; here it comes back false and the caller answers 409.
     *
     * `INSERT IGNORE` for the reason [addPoints] spells out: it is the only one of the two idioms whose row count tells
     * an insert from a duplicate, given how the app connects.
     */
    fun addMember(discordId: String, houseId: Int): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "INSERT IGNORE INTO $MEMBERS_TABLE(discord_id, house_id, joined) " +
                " VALUES (:discordId, :houseId, NOW()) "
        connection
            .query(query)
            .addParameter("discordId", discordId)
            .addParameter("houseId", houseId)
            .executeUpdate()

        // 1 on an insert, 0 when the row was already there and IGNORE dropped this one.
        connection.result == 1
    }

    /**
     * Records what a member wants for next season — the action and, for a `CHANGE`, the house it names. Nothing is
     * applied here; the season transition is what reads these back.
     *
     * [houseId] is written on every call, null included, and that is what keeps the two columns consistent: a player who
     * asked to change and then settles on `STAY` must not keep the target of the choice they replaced, or the next
     * `CHANGE` recorded without a house would inherit it. One statement, so the pair can never be half-written.
     *
     * Returns nothing on purpose. The obvious signal, the number of rows the UPDATE touched, cannot tell "no such
     * member" from "already set to that value": MySQL reports 0 rows changed for both, so a caller using it for its 404
     * would answer 404 to a player recording `LEAVE` twice. Existence is the caller's [member] read; the write is then
     * best-effort, and a membership deleted in between simply updates nothing.
     */
    fun setPendingAction(discordId: String, action: String?, houseId: Int? = null) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $MEMBERS_TABLE SET pending_action = :action, pending_house_id = :houseId " +
                    " WHERE discord_id = :discordId "
            connection
                .query(query)
                .addParameter("action", action)
                .addParameter("houseId", houseId)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    /** How far along its lifecycle a season is, or null when nothing has ever happened to it. */
    fun seasonState(season: String): HouseSeasonState? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $SEASONS_TABLE WHERE season = :season LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetchFirst(HouseSeasonState::class.java)
    }

    /**
     * Stamps a season as opened and answers whether this call is the one that did it. False means it was already open.
     *
     * Two statements because the row may not exist at all: `INSERT IGNORE` makes sure it does without disturbing one
     * that already did, then the UPDATE claims it.
     *
     * The row count is trustworthy here, unlike in [setPendingAction], and for a reason worth stating: the guard
     * `opened IS NULL` sits in the WHERE, and the SET is what makes it false. So a row matched is necessarily a row
     * changed, and it does not matter that the app's JDBC url reports rows *matched* rather than rows changed — both
     * counts agree. A second caller matches nothing and gets false.
     */
    fun openSeason(season: String): Boolean = DatabaseAccessor.withDao { connection ->
        connection
            .query("INSERT IGNORE INTO $SEASONS_TABLE(season) VALUES (:season)")
            .addParameter("season", season)
            .executeUpdate()

        connection
            .query("UPDATE $SEASONS_TABLE SET opened = NOW() WHERE season = :season AND opened IS NULL")
            .addParameter("season", season)
            .executeUpdate()

        connection.result == 1
    }

    /**
     * Stamps a season as closed and answers whether this call is the one that did it.
     *
     * `opened IS NOT NULL` is in the WHERE rather than checked by the caller, so a season that never ran cannot be
     * closed even by a caller that forgot to look — which is the whole of the first-deployment guard. Row count reads as
     * in [openSeason]: the predicate is in the WHERE and the SET falsifies it, so matched and changed coincide.
     */
    fun closeSeason(season: String): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $SEASONS_TABLE SET closed = NOW() " +
                " WHERE season = :season AND opened IS NOT NULL AND closed IS NULL "
        connection
            .query(query)
            .addParameter("season", season)
            .executeUpdate()

        connection.result == 1
    }

    /**
     * Claims the right to post today's ranking, and answers whether this call got it.
     *
     * The claim is made *before* the message is sent, which is the opposite trade from the season closure, and for a
     * reason worth writing down. This fires daily: a duplicate is spam, a miss is invisible. The closure fires yearly:
     * a duplicate is noise, a miss is a lost recap. So this one claims first and that one announces first.
     *
     * The trade is cheaper than it looks, because there is nothing to wait for anyway — `sendMessageEmbeds` hands the
     * message to JDA's queue and returns, so "write it after sending" would only ever mean "after enqueuing" and would
     * confirm nothing about delivery. Claiming first has the same meaning and cannot double-post.
     *
     * Row count reads as in [openSeason]: the predicate is in the WHERE and the SET falsifies it — `NOW()` is never
     * before the start of today — so matched and changed coincide. The 18 ticks that fit in a 7am-9am window therefore
     * produce one message, not 18.
     */
    fun claimDailyRanking(season: String, startOfDay: Date): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $SEASONS_TABLE SET last_ranking = NOW() " +
                " WHERE season = :season AND (last_ranking IS NULL OR last_ranking < :startOfDay) "
        connection
            .query(query)
            .addParameter("season", season)
            .addParameter("startOfDay", startOfDay)
            .executeUpdate()

        connection.result == 1
    }

    /** The members who recorded an intention for next season, in no particular order. */
    fun pendingMembers(): List<HouseMember> = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $MEMBERS_TABLE WHERE pending_action IS NOT NULL")
            .throwOnMappingFailure(false)
            .executeAndFetch(HouseMember::class.java)
            ?: listOf()
    }

    /**
     * Moves a member to [houseId], restamps `joined` and clears their intention, all in one statement.
     *
     * One statement on purpose. It makes applying a `CHANGE` atomic per member, so a season opening interrupted halfway
     * through leaves every member either fully moved or untouched, and the next tick — which selects on
     * `pending_action IS NOT NULL` — picks up exactly the ones still owed a move. Clearing the intention in a separate
     * pass would leave a window where a restart moves the same player twice, into a second house.
     *
     * `joined` is restamped because it is what the scanner filters games on: the player earns for the new house from now,
     * and the points they earned for the old one stay where they are.
     */
    fun changeHouse(discordId: String, houseId: Int) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $MEMBERS_TABLE " +
                    " SET house_id = :houseId, joined = NOW(), pending_action = NULL, pending_house_id = NULL " +
                    " WHERE discord_id = :discordId "
            connection
                .query(query)
                .addParameter("houseId", houseId)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    /** Removes a membership. The player's points stay in the register, credited to the house they were earned for. */
    fun removeMember(discordId: String) {
        DatabaseAccessor.withDao { connection ->
            connection
                .query("DELETE FROM $MEMBERS_TABLE WHERE discord_id = :discordId")
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    /**
     * Clears every remaining intention, which by then is every `STAY` — the two acted-on ones clear themselves as they
     * are applied. Idempotent, so a repeated season opening costs nothing.
     *
     * Both columns, and the WHERE tests both: a `CHANGE` the opening could not apply — one naming no house, or a house
     * since deleted — leaves its target behind, and it is this sweep that forgets it rather than the next season
     * inheriting a target nobody asked for any more.
     */
    fun clearPendingActions() {
        DatabaseAccessor.withDao { connection ->
            connection
                .query(
                    "UPDATE $MEMBERS_TABLE SET pending_action = NULL, pending_house_id = NULL " +
                            " WHERE pending_action IS NOT NULL OR pending_house_id IS NOT NULL"
                )
                .executeUpdate()
        }
    }

    private fun member(connection: Connection, discordId: String): HouseMember? = connection
        .query("SELECT * FROM $MEMBERS_TABLE WHERE discord_id = :discordId LIMIT 1")
        .throwOnMappingFailure(false)
        .addParameter("discordId", discordId)
        .executeAndFetchFirst(HouseMember::class.java)

    /** By internal id, which only ever comes from a `house_members` row — the API works from the slug. */
    private fun house(connection: Connection, id: Int): House? = connection
        .query("SELECT * FROM $HOUSES_TABLE WHERE id = :id LIMIT 1")
        .throwOnMappingFailure(false)
        .addParameter("id", id)
        .executeAndFetchFirst(House::class.java)

    private fun houseTotals(connection: Connection, season: String): List<HouseTotals> {
        val query = "SELECT h.id AS house_id, " +
                " (SELECT COUNT(*) FROM $MEMBERS_TABLE m WHERE m.house_id = h.id) AS member_count, " +
                " (SELECT COALESCE(SUM($TOTAL_SUM), 0) FROM $POINTS_TABLE p " +
                "   WHERE p.house_id = h.id AND p.season = :season) AS total_points " +
                " FROM $HOUSES_TABLE h ORDER BY h.id "
        return connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetch(HouseTotals::class.java)
            ?: listOf()
    }

    /**
     * Current members with the points they earned for the house they are in now, unranked. [houseId] null means every
     * house at once, which is what [standings] needs.
     *
     * LEFT JOIN on the points, so a member who has scored nothing is still in the list with zeroes. INNER JOIN on the
     * Discord profile, because a member with no profile row has no name and no avatar to show and should not appear.
     */
    private fun rankedMembers(connection: Connection, season: String, houseId: Int?): List<HouseRankedMember> {
        val houseFilter = if (houseId == null) "" else " WHERE m.house_id = :houseId "
        val sql = "SELECT m.discord_id, m.house_id, d.discord_name, d.discord_avatar, " + POINTS_SUMS +
                " FROM $MEMBERS_TABLE m " +
                " JOIN $DISCORD_TABLE d ON d.discord_id = m.discord_id " +
                " LEFT JOIN $POINTS_TABLE p " +
                "   ON p.discord_id = m.discord_id AND p.house_id = m.house_id AND p.season = :season " +
                houseFilter +
                " GROUP BY m.discord_id, m.house_id, d.discord_name, d.discord_avatar "

        val query = connection
            .query(sql)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
        if (houseId != null) query.addParameter("houseId", houseId)

        return query.executeAndFetch(HouseRankedMember::class.java) ?: listOf()
    }

    /**
     * Sorts best first and stamps a competition rank: equal totals share a rank and the next one skips, so two players
     * on 40 points are both 2nd and the next is 4th. Ties break on name, to keep the list stable between calls.
     */
    private fun List<HouseRankedMember>.withRanks(): List<HouseRankedMember> {
        var lastTotal = -1
        var lastRank = 0
        return this
            .sortedWith(
                compareByDescending<HouseRankedMember> { it.total() }
                    .thenBy { it.discordName ?: it.discordId }
            )
            .mapIndexed { index, member ->
                if (member.total() != lastTotal) {
                    lastRank = index + 1
                    lastTotal = member.total()
                }
                member.copy(rank = lastRank)
            }
    }
}
