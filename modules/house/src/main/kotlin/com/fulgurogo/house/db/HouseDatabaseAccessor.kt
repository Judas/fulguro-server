package com.fulgurogo.house.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.house.db.model.*
import org.sql2o.Connection

/**
 * Every read the house module makes. The write paths arrive with the scanner and the API mutations.
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
