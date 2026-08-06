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

    /** The player's membership, or null when they are in no house. */
    fun member(discordId: String): HouseMember? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $MEMBERS_TABLE WHERE discord_id = :discordId LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(HouseMember::class.java)
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
     * How many members each house has, for the balanced draw.
     *
     * Driven from `houses` with a LEFT JOIN rather than grouping `house_members`, so that an empty house comes back
     * with a count of 0 instead of being absent from the map. Getting that wrong is not loud: a missing key read as
     * "no data" instead of "no members" makes the draw skip the emptiest house, which is the one it exists to pick.
     *
     * `COUNT(m.discord_id)` and not `COUNT(*)`, for the same reason and just as quietly: on the unmatched row of a LEFT
     * JOIN, `COUNT(*)` counts the row itself and answers 1 for a house with nobody in it.
     */
    fun memberCounts(): Map<Int, Int> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT h.id AS house_id, COUNT(m.discord_id) AS member_count " +
                " FROM $HOUSES_TABLE h " +
                " LEFT JOIN $MEMBERS_TABLE m ON m.house_id = h.id " +
                " GROUP BY h.id "
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .executeAndFetch(HouseTotals::class.java)
            ?.associate { it.houseId to it.memberCount }
            ?: mapOf()
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
     * A player's points for the season, summed per type. All zeroes when they have no house or have scored nothing.
     *
     * Matched on the player's current house, like [ranking], so the figure always agrees with the rank shown next to
     * it. Within one season that match changes nothing — a house change is only ever applied when a season opens — but
     * it costs nothing and does not rely on that staying true.
     */
    fun playerPoints(season: String, discordId: String): HousePointsTotal = DatabaseAccessor.withDao { connection ->
        val query = "SELECT $POINTS_SUMS " +
                " FROM $MEMBERS_TABLE m " +
                " LEFT JOIN $POINTS_TABLE p " +
                "   ON p.discord_id = m.discord_id AND p.house_id = m.house_id AND p.season = :season " +
                " WHERE m.discord_id = :discordId "
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(HousePointsTotal::class.java)
            ?: HousePointsTotal(0, 0, 0, 0, 0, 0, 0)
    }

    /**
     * The player's rank inside their own house, or null when they are in no house.
     *
     * Built on top of [ranking] rather than given its own SQL: the two would otherwise both have to implement tie
     * handling and agree on it, and one house is at most a few dozen rows.
     */
    fun playerRank(season: String, discordId: String): Int? {
        val member = member(discordId) ?: return null
        return ranking(season, member.houseId)
            .firstOrNull { it.discordId == discordId }
            ?.rank
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
     * - **The season window** puts July and August games permanently out of reach, which is "no points outside the
     *   season" with no extra state. It is a window over the *game* date, so a late-June game scanned in July still
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
     * `ON DUPLICATE KEY UPDATE gold_id = gold_id` rather than `INSERT IGNORE`: both make a second pass over the same
     * game a no-op, but `INSERT IGNORE` also turns a genuine failure — a value too wide for its column, say — into a
     * warning nobody reads, and a point row lost that way would never be retried, since the game counts as scored the
     * moment any row for it exists.
     *
     * `scored_at` comes from `NOW()`, so the register is stamped by the database clock rather than by a JVM that might
     * be in another zone. Nothing reads it yet: with no anti-farming cap in this delivery, it is what makes one
     * computable after the fact, mid-season, without a migration.
     */
    fun addPoints(points: List<HousePoints>): Int {
        if (points.isEmpty()) return 0

        return DatabaseAccessor.withDao { connection ->
            val sql = "INSERT INTO $POINTS_TABLE( " +
                    " gold_id, discord_id, house_id, season, " +
                    " played, gold_opponent, rival_house, long_game, victory, even_game, ranked, scored_at) " +
                    " VALUES (:goldId, :discordId, :houseId, :season, " +
                    " :played, :goldOpponent, :rivalHouse, :longGame, :victory, :evenGame, :ranked, NOW()) " +
                    " ON DUPLICATE KEY UPDATE gold_id = gold_id "

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

                // 1 on an insert, 0 when the row was already there and the update changed nothing.
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
     * `ON DUPLICATE KEY UPDATE discord_id = discord_id` and not `INSERT IGNORE`, for the reason [addPoints] spells out:
     * a genuine failure must not be downgraded to a warning nobody reads.
     */
    fun addMember(discordId: String, houseId: Int): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "INSERT INTO $MEMBERS_TABLE(discord_id, house_id, joined) " +
                " VALUES (:discordId, :houseId, NOW()) " +
                " ON DUPLICATE KEY UPDATE discord_id = discord_id "
        connection
            .query(query)
            .addParameter("discordId", discordId)
            .addParameter("houseId", houseId)
            .executeUpdate()

        // 1 on an insert, 0 when the row was already there and the update changed nothing.
        connection.result == 1
    }

    /**
     * Records what a member wants for next season, or clears it with a null [action]. Nothing is applied here — the
     * season transition is what reads these back.
     *
     * Returns nothing on purpose. The obvious signal, the number of rows the UPDATE touched, cannot tell "no such
     * member" from "already set to that value": MySQL reports 0 rows changed for both, so a caller using it for its 404
     * would answer 404 to a player recording `LEAVE` twice. Existence is the caller's [member] read; the write is then
     * best-effort, and a membership deleted in between simply updates nothing.
     */
    fun setPendingAction(discordId: String, action: String?) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $MEMBERS_TABLE SET pending_action = :action WHERE discord_id = :discordId "
            connection
                .query(query)
                .addParameter("action", action)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

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
