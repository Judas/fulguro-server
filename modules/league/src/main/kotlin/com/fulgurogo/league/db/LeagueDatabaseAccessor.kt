package com.fulgurogo.league.db

import com.fulgurogo.common.db.DatabaseAccessor
import com.fulgurogo.common.db.query
import com.fulgurogo.league.LeaguePairing
import com.fulgurogo.league.LeagueSession
import com.fulgurogo.league.db.model.*
import org.sql2o.Connection
import java.util.*

/**
 * Every read and write the league module makes.
 *
 * The aggregates take the season as a parameter, which is why they are queries here and not views: a view cannot be told
 * which season is current, and that is computed in Kotlin. The upside on deploy day is that the league adds no view, so
 * there is nothing to swap on the production server.
 *
 * The same split as the houses between the two languages, for the same reason: SQL counts, Kotlin totals and ranks. The
 * order a caller displays, the total it prints and the rank it shows therefore all come from [LeagueRenown.total] rather
 * than from an ORDER BY that could drift from it.
 *
 * Several writes return "did this call do it?" rather than a row count, and they are trustworthy for one reason worth
 * stating once: the guard sits in the `WHERE` and the `SET` is what falsifies it, so a row matched is necessarily a row
 * changed. That matters because the app's JDBC url leaves `useAffectedRows` at its default and the driver reports rows
 * *matched* — with the guard in the WHERE, matched and changed coincide.
 */
object LeagueDatabaseAccessor {
    private const val SEASONS_TABLE = "league_seasons"
    private const val SESSIONS_TABLE = "league_sessions"
    private const val PLAYERS_TABLE = "league_players"
    private const val MEMBERS_TABLE = "league_members"
    private const val MATCHES_TABLE = "league_matches"
    private const val EXEMPTIONS_TABLE = "league_exemptions"

    private const val HOUSE_MEMBERS_TABLE = "house_members"
    private const val OGS_USER_TABLE = "ogs_user_info"
    private const val GOLD_RATINGS_TABLE = "gold_ratings"
    private const val DISCORD_TABLE = "discord_user_info"

    // ---------------------------------------------------------------------------------------------------------------
    // Seasons and sessions
    // ---------------------------------------------------------------------------------------------------------------

    /** How far along its lifecycle a season is, or null when nothing has ever happened to it. */
    fun seasonState(season: String): LeagueSeasonState? = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $SEASONS_TABLE WHERE season = :season LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetchFirst(LeagueSeasonState::class.java)
    }

    /** A session's guards, or null when it has never been drawn — in which case it has no row at all. */
    fun sessionState(season: String, session: Int): LeagueSessionState? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $SESSIONS_TABLE WHERE season = :season AND session = :session LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeAndFetchFirst(LeagueSessionState::class.java)
    }

    /**
     * Every session of the season that has a row, in order.
     *
     * One query rather than sixteen: the calendar page needs the state of all of them at once, and asking per session
     * would be sixteen round trips for a page that renders a table. Sessions never drawn are simply absent — they have no
     * row — so the caller indexes this by number and reads a miss as "not drawn yet".
     */
    fun sessionStates(season: String): List<LeagueSessionState> = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $SESSIONS_TABLE WHERE season = :season ORDER BY session")
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetch(LeagueSessionState::class.java)
            ?: listOf()
    }

    /**
     * Stamps a season as opened and answers whether this call is the one that did it.
     *
     * Two statements because the row may not exist: `INSERT IGNORE` makes sure it does without disturbing one that
     * already did, then the UPDATE claims it. Exactly `HouseDatabaseAccessor.openSeason`.
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
     * `opened IS NOT NULL` is in the WHERE rather than checked by the caller, so a season that never ran cannot be closed
     * even by a caller that forgot to look. That is the whole of the first-deployment guard.
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
     * Claims the right to draw a session, and answers whether this call got it.
     *
     * The row is created here, and nowhere else, which is what makes "no row" mean "never drawn". Claiming *before*
     * drawing is the right trade for this one: the draw creates matches on OGS and sends invitation links, so a duplicate
     * would be two challenges per player, while a miss is caught by the next tick ten minutes later.
     */
    fun claimDraw(season: String, session: Int): Boolean = DatabaseAccessor.withDao { connection ->
        connection
            .query("INSERT IGNORE INTO $SESSIONS_TABLE(season, session) VALUES (:season, :session)")
            .addParameter("season", season)
            .addParameter("session", session)
            .executeUpdate()

        val query = "UPDATE $SESSIONS_TABLE SET drawn = NOW() " +
                " WHERE season = :season AND session = :session AND drawn IS NULL "
        connection
            .query(query)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeUpdate()

        connection.result == 1
    }

    /**
     * Claims the right to redraw a session that came out empty, at most once a day.
     *
     * No `INSERT IGNORE` and no null branch, on purpose: a redraw only ever applies to a session that has already been
     * drawn, so a missing row must answer false rather than quietly create one. The comparison is against the start of
     * today for the same reason `claimDailyRanking` uses it — the 12 ticks of a 7am-9am window have to produce one redraw.
     */
    fun claimRedraw(season: String, session: Int, startOfDay: Date): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $SESSIONS_TABLE SET drawn = NOW() " +
                " WHERE season = :season AND session = :session AND drawn IS NOT NULL AND drawn < :startOfDay "
        connection
            .query(query)
            .addParameter("season", season)
            .addParameter("session", session)
            .addParameter("startOfDay", startOfDay)
            .executeUpdate()

        connection.result == 1
    }

    /**
     * Claims the right to announce a draw, and answers whether this call got it.
     *
     * Unlike [claimDraw] there is no insert: a session nobody has drawn has nothing to announce, so a missing row
     * correctly answers false.
     */
    fun claimNotification(season: String, session: Int): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $SESSIONS_TABLE SET notified = NOW() " +
                " WHERE season = :season AND session = :session AND drawn IS NOT NULL AND notified IS NULL "
        connection
            .query(query)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeUpdate()

        connection.result == 1
    }

    /**
     * Claims the right to settle a session, and answers whether this call got it.
     *
     * The one claim whose loss is permanent, since [markUnplayed] closes matches for good — so it is taken first and the
     * settlement runs under it, never the other way round.
     */
    fun claimSettlement(season: String, session: Int): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $SESSIONS_TABLE SET settled = NOW() " +
                " WHERE season = :season AND session = :session AND drawn IS NOT NULL AND settled IS NULL "
        connection
            .query(query)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeUpdate()

        connection.result == 1
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Players and members
    // ---------------------------------------------------------------------------------------------------------------

    /** The player's OGS-side row, or null when they have never joined the league. */
    fun player(discordId: String): LeaguePlayer? = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $PLAYERS_TABLE WHERE discord_id = :discordId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(LeaguePlayer::class.java)
    }

    /** The players still owed a `PUT member/{id}` at OGS. The whole of that queue's bookkeeping is this one column. */
    fun playersToRegister(): List<LeaguePlayer> = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $PLAYERS_TABLE WHERE ogs_registered IS NULL")
            .throwOnMappingFailure(false)
            .executeAndFetch(LeaguePlayer::class.java)
            ?: listOf()
    }

    /**
     * Creates the OGS-side row, and answers whether this call created it.
     *
     * False means the player already had one, which is the normal case for anyone who has been in the league before —
     * their `ogs_registered` is kept, so rejoining costs no OGS call.
     */
    fun addPlayer(discordId: String): Boolean = DatabaseAccessor.withDao { connection ->
        connection
            .query("INSERT IGNORE INTO $PLAYERS_TABLE(discord_id) VALUES (:discordId)")
            .addParameter("discordId", discordId)
            .executeUpdate()

        // 1 on an insert, 0 when the row was already there and IGNORE dropped this one.
        connection.result == 1
    }

    /** Records that OGS knows this member. `ogs_registered IS NULL` in the WHERE keeps the first stamp. */
    fun markRegistered(discordId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $PLAYERS_TABLE SET ogs_registered = NOW() " +
                    " WHERE discord_id = :discordId AND ogs_registered IS NULL "
            connection
                .query(query)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    /** The player's academy row for this season, or null when they are not in it. */
    fun member(season: String, discordId: String): LeagueMember? = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $MEMBERS_TABLE WHERE season = :season AND discord_id = :discordId LIMIT 1"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("discordId", discordId)
            .executeAndFetchFirst(LeagueMember::class.java)
    }

    /** Everyone who joined this season's academy, active or not. */
    fun members(season: String): List<LeagueMember> = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $MEMBERS_TABLE WHERE season = :season")
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetch(LeagueMember::class.java)
            ?: listOf()
    }

    /**
     * Puts a player in this season's academy, and answers whether this call created the row.
     *
     * False means they had already joined this season — including if they have since left, which is why the join route
     * follows this with [setActive] rather than treating false as an error. `joined` is therefore stamped once per
     * season and never restamped, so a player who leaves and comes back keeps the date they first entered.
     */
    fun addMember(season: String, discordId: String): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "INSERT IGNORE INTO $MEMBERS_TABLE(season, discord_id, joined) " +
                " VALUES (:season, :discordId, NOW()) "
        connection
            .query(query)
            .addParameter("season", season)
            .addParameter("discordId", discordId)
            .executeUpdate()

        // 1 on an insert, 0 when the row was already there and IGNORE dropped this one.
        connection.result == 1
    }

    /**
     * Puts a member in or out of the running.
     *
     * Leaving stamps `left_since`; coming back does **not** clear it. It is the trace of the last departure rather than a
     * statement about the present — `active` is what says where the player stands, and the two together are what let a
     * "left and came back" be told apart from a "never left" months later.
     *
     * Returns nothing, for the reason `HouseDatabaseAccessor.setPendingAction` spells out: the row count cannot tell "no
     * such member" from "already in that state", so it would be a misleading 404. Existence is the caller's [member] read.
     */
    fun setActive(season: String, discordId: String, active: Boolean) {
        DatabaseAccessor.withDao { connection ->
            val leftSince = if (active) "" else ", left_since = NOW() "
            val query = "UPDATE $MEMBERS_TABLE SET active = :active $leftSince " +
                    " WHERE season = :season AND discord_id = :discordId "
            connection
                .query(query)
                .addParameter("active", active)
                .addParameter("season", season)
                .addParameter("discordId", discordId)
                .executeUpdate()
        }
    }

    /**
     * The active members the draw can actually pair.
     *
     * The four eligibility conditions live in this one join — an academy row, a house, an OGS account registered with the
     * OGS league, and a usable gold rating. Keeping them together is the point: split between the query and the draw, a
     * player eventually gets paired against somebody OGS has never heard of, and the match creation then fails for a
     * reason nothing in the draw explains.
     *
     * `rating > 0 AND error = 0` is the "usable" part, and it is not paranoia: `GoldService` writes a row for every
     * linked player, so an unrated one sits at 0 and would be dragged to the bottom of the ladder and paired against the
     * same wrong opponent every session.
     *
     * ⚠ Nothing restricts this to test accounts. The dev sandbox that used to is gone — OGS notifies nobody, so a match
     * created from dev disturbs no player — which means a local run against a populated `fg_dev` academy really does
     * create matches on the shared OGS league. What keeps the two apart is the `db.name` prefix of `league_match_id`, and
     * that guards against collisions, not against volume.
     */
    fun candidates(season: String): List<LeagueCandidate> = DatabaseAccessor.withDao { connection ->
        val sql = "SELECT m.discord_id, h.house_id, g.rating " +
                " FROM $MEMBERS_TABLE m " +
                " JOIN $HOUSE_MEMBERS_TABLE h ON h.discord_id = m.discord_id " +
                " JOIN $OGS_USER_TABLE o ON o.discord_id = m.discord_id " +
                " JOIN $GOLD_RATINGS_TABLE g ON g.discord_id = m.discord_id " +
                " JOIN $PLAYERS_TABLE p ON p.discord_id = m.discord_id " +
                " WHERE m.season = :season AND m.active = 1 " +
                "   AND p.ogs_registered IS NOT NULL " +
                "   AND g.rating > 0 AND g.error = 0 "

        connection
            .query(sql)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetch(LeagueCandidate::class.java)
            ?: listOf()
    }

    /**
     * Whether the player has an OGS account linked, which the join route needs and no other module exposes by Discord id
     * — `OgsDatabaseAccessor.user` is keyed on the OGS id.
     *
     * Read here rather than by adding a method to the ogs module, for consistency with [candidates], which already joins
     * this table: the league reads the four tables its eligibility depends on, and does so in one file.
     */
    fun isLinkedToOgs(discordId: String): Boolean = DatabaseAccessor.withDao { connection ->
        val query = "SELECT COUNT(*) FROM $OGS_USER_TABLE WHERE discord_id = :discordId"
        (connection.query(query).addParameter("discordId", discordId).executeScalar(Int::class.java) ?: 0) > 0
    }

    /**
     * Drops the members who are no longer eligible — no OGS account, or no house — and answers how many.
     *
     * A state reconciliation rather than a call to make from each exit path, and that is the point: it works **however
     * the row disappeared**, including through a future unlink route nobody thought to wire into the league. The cost is
     * up to one tick of latency, which nothing notices since only the draw reads `active`.
     *
     * The real trigger is not hypothetical. `CleanService.removeDeletedAccounts` runs every tick and does
     * `DELETE FROM ogs_user_info WHERE ogs_name LIKE 'deleted-%'` without touching anything else, so a player who deletes
     * their OGS account loses the link while keeping their academy row. There is no unlink route in the API at all —
     * `Api.link` adds, nothing removes — so this is the path that actually happens.
     *
     * Leaving Discord is handled elsewhere, by `CleanDatabaseAccessor.removeAllFrom`, which deletes the academy row
     * outright.
     */
    fun deactivateIneligible(season: String): Int = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $MEMBERS_TABLE m SET m.active = 0, m.left_since = NOW() " +
                " WHERE m.season = :season AND m.active = 1 " +
                "   AND (NOT EXISTS (SELECT 1 FROM $OGS_USER_TABLE o WHERE o.discord_id = m.discord_id) " +
                "     OR NOT EXISTS (SELECT 1 FROM $HOUSE_MEMBERS_TABLE h WHERE h.discord_id = m.discord_id)) "
        connection
            .query(query)
            .addParameter("season", season)
            .executeUpdate()

        connection.result
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Matches
    // ---------------------------------------------------------------------------------------------------------------

    /** Every match of the season, in session order. */
    fun matches(season: String): List<LeagueMatch> = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $MATCHES_TABLE WHERE season = :season ORDER BY session")
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .executeAndFetch(LeagueMatch::class.java)
            ?: listOf()
    }

    /** Every match of one session. */
    fun matches(season: String, session: Int): List<LeagueMatch> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $MATCHES_TABLE WHERE season = :season AND session = :session"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeAndFetch(LeagueMatch::class.java)
            ?: listOf()
    }

    /** One player's matches over the season, whichever colour they had. */
    fun matchesOf(season: String, discordId: String): List<LeagueMatch> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $MATCHES_TABLE " +
                " WHERE season = :season " +
                "   AND (black_discord_id = :discordId OR white_discord_id = :discordId) " +
                " ORDER BY session "
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("discordId", discordId)
            .executeAndFetch(LeagueMatch::class.java)
            ?: listOf()
    }

    /**
     * The match OGS's callback names, or null.
     *
     * Keyed on the OGS match id, which is the id the callback carries. Not season-scoped, because a callback arrives with
     * nothing but that id — which is also why the column is indexed.
     */
    fun matchByOgsId(ogsMatchId: Int): LeagueMatch? = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $MATCHES_TABLE WHERE ogs_match_id = :ogsMatchId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("ogsMatchId", ogsMatchId)
            .executeAndFetchFirst(LeagueMatch::class.java)
    }

    /**
     * The match by the id we gave OGS, or null.
     *
     * The other way into a callback, kept because it is not settled which id OGS substitutes into the template. This one
     * is unique across every season and both environments, the prefix being what makes that true.
     */
    fun matchByLeagueId(leagueMatchId: String): LeagueMatch? = DatabaseAccessor.withDao { connection ->
        connection
            .query("SELECT * FROM $MATCHES_TABLE WHERE league_match_id = :leagueMatchId LIMIT 1")
            .throwOnMappingFailure(false)
            .addParameter("leagueMatchId", leagueMatchId)
            .executeAndFetchFirst(LeagueMatch::class.java)
    }

    /** The matches of a session whose fate is still open: no result either way. For the catch-up and the settlement. */
    fun pendingMatches(season: String, session: Int): List<LeagueMatch> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $MATCHES_TABLE " +
                " WHERE season = :season AND session = :session AND result IS NULL "
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeAndFetch(LeagueMatch::class.java)
            ?: listOf()
    }

    /**
     * The matches of a session with a challenge to send and at least one DM still owed.
     *
     * `ogs_match_id IS NOT NULL` because there is nothing to send before OGS has created the challenge — without it a
     * match whose creation failed would be picked up every tick and reported as a DM failure rather than as what it is.
     *
     * `result IS NULL` because a challenge whose fate is already decided is not worth sending. A player whose DM never
     * landed on a match that has since been played knows perfectly well it happened, and one whose match the settlement
     * voided would be handed a link with a deadline that has passed — an invitation to a game that cannot count.
     */
    fun unnotifiedMatches(season: String, session: Int): List<LeagueMatch> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $MATCHES_TABLE " +
                " WHERE season = :season AND session = :session " +
                "   AND ogs_match_id IS NOT NULL " +
                "   AND result IS NULL " +
                "   AND (black_notified IS NULL OR white_notified IS NULL) "
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeAndFetch(LeagueMatch::class.java)
            ?: listOf()
    }

    /**
     * How many times each pair has already been drawn this season, for the draw's repeat penalty.
     *
     * Keyed with [LeaguePairing.opponentKey] so a lookup finds the pair whichever way round it is asked for. Getting that wrong is
     * silent — every lookup would miss, the penalty would never apply, and the same two players would be paired all
     * season — which is why the key is built by a function rather than by each caller.
     *
     * Folded over [matches] rather than grouped in SQL, so there is one definition of "the matches of this season" and
     * the penalty cannot be computed over a different set than the standings are.
     */
    fun pastOpponents(season: String): Map<Pair<String, String>, Int> = matches(season)
        .groupingBy { LeaguePairing.opponentKey(it.blackDiscordId, it.whiteDiscordId) }
        .eachCount()

    /**
     * Writes the pairings a draw produced, and answers how many were new.
     *
     * `INSERT IGNORE`, the idiom the rest of the app uses, and for the reason `HouseDatabaseAccessor.addPoints` spells
     * out: it is the only one of the two whose row count tells an insert from a duplicate, given that the app's JDBC url
     * reports rows matched. So a draw run twice reports 0 new matches rather than looking like it worked.
     *
     * Only the columns a draw knows. The OGS side is filled afterwards by [setMatchChallenge], which is what makes an
     * interrupted draw resumable: the rows are already there, and replaying the OGS call returns the same challenge.
     */
    fun addMatches(matches: List<LeagueMatch>): Int {
        if (matches.isEmpty()) return 0

        return DatabaseAccessor.withDao { connection ->
            val sql = "INSERT IGNORE INTO $MATCHES_TABLE( " +
                    " season, session, black_discord_id, white_discord_id, " +
                    " black_house_id, white_house_id, pairing_score, league_match_id, created) " +
                    " VALUES (:season, :session, :blackDiscordId, :whiteDiscordId, " +
                    " :blackHouseId, :whiteHouseId, :pairingScore, :leagueMatchId, NOW()) "

            var inserted = 0
            matches.forEach { match ->
                connection
                    .query(sql)
                    .addParameter("season", match.season)
                    .addParameter("session", match.session)
                    .addParameter("blackDiscordId", match.blackDiscordId)
                    .addParameter("whiteDiscordId", match.whiteDiscordId)
                    .addParameter("blackHouseId", match.blackHouseId)
                    .addParameter("whiteHouseId", match.whiteHouseId)
                    .addParameter("pairingScore", match.pairingScore)
                    .addParameter("leagueMatchId", match.leagueMatchId)
                    .executeUpdate()

                if (connection.result == 1) inserted++
            }
            inserted
        }
    }

    /** Records the challenge OGS created: its id and the three links. */
    fun setMatchChallenge(
        season: String,
        session: Int,
        blackDiscordId: String,
        ogsMatchId: Int,
        blackInvite: String?,
        whiteInvite: String?,
        spectatorLink: String?
    ) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $MATCHES_TABLE " +
                    " SET ogs_match_id = :ogsMatchId, black_invite = :blackInvite, " +
                    "     white_invite = :whiteInvite, spectator_link = :spectatorLink " +
                    " WHERE season = :season AND session = :session AND black_discord_id = :blackDiscordId "
            connection
                .query(query)
                .addParameter("ogsMatchId", ogsMatchId)
                .addParameter("blackInvite", blackInvite)
                .addParameter("whiteInvite", whiteInvite)
                .addParameter("spectatorLink", spectatorLink)
                .addParameter("season", season)
                .addParameter("session", session)
                .addParameter("blackDiscordId", blackDiscordId)
                .executeUpdate()
        }
    }

    /**
     * Dates the DM that went out to one side.
     *
     * Which column is picked by [side] rather than by the caller writing a column name, because the two columns are
     * interchangeable to a compiler and swapping them would read as "the DM was sent" for a player who never got one.
     */
    fun markNotified(season: String, session: Int, blackDiscordId: String, side: LeagueSide) {
        DatabaseAccessor.withDao { connection ->
            val column = when (side) {
                LeagueSide.BLACK -> "black_notified"
                LeagueSide.WHITE -> "white_notified"
            }
            val query = "UPDATE $MATCHES_TABLE SET $column = NOW() " +
                    " WHERE season = :season AND session = :session AND black_discord_id = :blackDiscordId "
            connection
                .query(query)
                .addParameter("season", season)
                .addParameter("session", session)
                .addParameter("blackDiscordId", blackDiscordId)
                .executeUpdate()
        }
    }

    /** Links the match to the OGS game that came out of it, and to the gold id the rest of the app knows it by. */
    fun setMatchGame(season: String, session: Int, blackDiscordId: String, ogsGameId: Int, goldId: String) {
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $MATCHES_TABLE SET ogs_game_id = :ogsGameId, gold_id = :goldId " +
                    " WHERE season = :season AND session = :session AND black_discord_id = :blackDiscordId "
            connection
                .query(query)
                .addParameter("ogsGameId", ogsGameId)
                .addParameter("goldId", goldId)
                .addParameter("season", season)
                .addParameter("session", session)
                .addParameter("blackDiscordId", blackDiscordId)
                .executeUpdate()
        }
    }

    /**
     * Records the outcome of a match, and answers whether this call is the one that recorded it.
     *
     * `result IS NULL` in the WHERE is what makes [LeagueMatch.UNPLAYED] terminal, and it is the load-bearing half of the
     * "not played, not replayable" rule: a game finished on OGS after its session was settled finds the row already
     * closed and changes nothing. It is also what makes the answer usable for announcing once — two writers reaching the
     * same result, the callback and the catch-up, cannot both report true.
     */
    fun finishMatch(season: String, session: Int, blackDiscordId: String, result: String): Boolean =
        DatabaseAccessor.withDao { connection ->
            val query = "UPDATE $MATCHES_TABLE SET result = :result, finished = NOW() " +
                    " WHERE season = :season AND session = :session AND black_discord_id = :blackDiscordId " +
                    "   AND result IS NULL "
            connection
                .query(query)
                .addParameter("result", result)
                .addParameter("season", season)
                .addParameter("session", session)
                .addParameter("blackDiscordId", blackDiscordId)
                .executeUpdate()

            connection.result == 1
        }

    /**
     * Closes every still-open match of a session as never played, and answers how many. The only destructive write in
     * the module.
     *
     * No condition on `ogs_game_id`: by the time this runs, a game started before the deadline has had its seven hours
     * to finish and be ingested. The point is that it leaves **no** match at null — a match pending forever is neither
     * played nor exempted, so it silently costs both its players the perfect-attendance bonus, and that would only
     * surface in May.
     */
    fun markUnplayed(season: String, session: Int): Int = DatabaseAccessor.withDao { connection ->
        val query = "UPDATE $MATCHES_TABLE SET result = :unplayed, finished = NOW() " +
                " WHERE season = :season AND session = :session AND result IS NULL "
        connection
            .query(query)
            .addParameter("unplayed", LeagueMatch.UNPLAYED)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeUpdate()

        connection.result
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Exemptions
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Writes the exemptions a draw constated, and answers how many were new. Same `INSERT IGNORE` idiom, same reason.
     *
     * Called by the draw and by nothing else. The settlement must never write here: an exemption is a decision of the
     * draw, not a consequence of a match that was not played.
     */
    fun addExemptions(exemptions: List<LeagueExemption>): Int {
        if (exemptions.isEmpty()) return 0

        return DatabaseAccessor.withDao { connection ->
            val sql = "INSERT IGNORE INTO $EXEMPTIONS_TABLE(season, session, discord_id, reason, created) " +
                    " VALUES (:season, :session, :discordId, :reason, NOW()) "

            var inserted = 0
            exemptions.forEach { exemption ->
                connection
                    .query(sql)
                    .addParameter("season", exemption.season)
                    .addParameter("session", exemption.session)
                    .addParameter("discordId", exemption.discordId)
                    .addParameter("reason", exemption.reason)
                    .executeUpdate()

                if (connection.result == 1) inserted++
            }
            inserted
        }
    }

    /**
     * How many sessions each player was exempted from, for the perfect-attendance bonus. Players with none are absent.
     *
     * Counted in Kotlin over the rows rather than with a `GROUP BY`, which keeps one query shape on this table and one
     * definition of what a season's exemptions are.
     */
    fun exemptions(season: String): Map<String, Int> = DatabaseAccessor.withDao { connection ->
        exemptionRows(connection, season)
            .groupingBy { it.discordId }
            .eachCount()
    }

    /**
     * The exemptions of one session.
     *
     * Two callers, both needing the rows rather than a count: the draw announcement, which names the players on the bench,
     * and the empty-session check, for which "no match **and** no exemption" is what tells a crashed draw from one that
     * was legitimately empty — a session with exemptions and no match was drawn, it just had nobody to pair.
     */
    fun exemptionsOf(season: String, session: Int): List<LeagueExemption> = DatabaseAccessor.withDao { connection ->
        val query = "SELECT * FROM $EXEMPTIONS_TABLE WHERE season = :season AND session = :session"
        connection
            .query(query)
            .throwOnMappingFailure(false)
            .addParameter("season", season)
            .addParameter("session", session)
            .executeAndFetch(LeagueExemption::class.java)
            ?: listOf()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Standings
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The season's standings, best first, each line carrying its renown broken down and its rank.
     *
     * Two reads on one connection — the tallies and the exemptions — so that a player's matches and their exemptions are
     * counted at the same instant. A draw landing between two connections could otherwise produce a line whose
     * perfect-attendance bonus was decided on a session count the played count does not include.
     *
     * The session count comes from [LeagueSession], not from a literal 16: the bonus is the one figure in the module that
     * has to agree with the calendar, and the calendar is where it is defined.
     */
    fun standings(season: String): List<LeagueStanding> = DatabaseAccessor.withDao { connection ->
        val exempted = exemptionRows(connection, season)
            .groupingBy { it.discordId }
            .eachCount()
        val sessionCount = LeagueSession.count(season)

        tallies(connection, season)
            .map { tally ->
                val exemptions = exempted[tally.discordId] ?: 0
                LeagueStanding(
                    discordId = tally.discordId,
                    discordName = tally.discordName,
                    discordAvatar = tally.discordAvatar,
                    houseId = tally.houseId,
                    active = tally.active,
                    played = tally.played,
                    won = tally.won,
                    lost = tally.lost,
                    exempted = exemptions,
                    renown = LeagueRenown.of(tally.played, tally.won, exemptions, sessionCount)
                )
            }
            .withRanks()
    }

    /**
     * What each academy has earned this season, by the house **frozen on each match**.
     *
     * Grouped on `black_house_id` / `white_house_id` and not on today's `house_members`, which is the whole point: an
     * academy's total must not move when a player leaves it or changes house. Same property as `house_points`, same
     * reason.
     *
     * `UNION ALL` and not `UNION`, for the reason [tallies] spells out: the branches carry no session, so two matches with
     * the same outcome for the same house are identical rows and `UNION` would silently merge them.
     *
     * Houses with no match at all are absent rather than present with zeroes — the caller drives its list from `houses`,
     * so a missing key reads as 0 there.
     */
    fun academyStandings(season: String): List<LeagueAcademyStanding> = DatabaseAccessor.withDao { connection ->
        val sql = "SELECT s.house_id, SUM(s.played) AS played, SUM(s.won) AS won FROM ( " +
                "   SELECT black_house_id AS house_id, " +
                "          CASE WHEN result IS NOT NULL AND result <> :unplayed THEN 1 ELSE 0 END AS played, " +
                "          CASE WHEN result = :blackWins THEN 1 ELSE 0 END AS won " +
                "   FROM $MATCHES_TABLE WHERE season = :season " +
                "   UNION ALL " +
                "   SELECT white_house_id AS house_id, " +
                "          CASE WHEN result IS NOT NULL AND result <> :unplayed THEN 1 ELSE 0 END AS played, " +
                "          CASE WHEN result = :whiteWins THEN 1 ELSE 0 END AS won " +
                "   FROM $MATCHES_TABLE WHERE season = :season " +
                " ) s GROUP BY s.house_id "

        connection
            .query(sql)
            .throwOnMappingFailure(false)
            .addParameter("unplayed", LeagueMatch.UNPLAYED)
            .addParameter("blackWins", LeagueMatch.BLACK_WINS)
            .addParameter("whiteWins", LeagueMatch.WHITE_WINS)
            .addParameter("season", season)
            .executeAndFetch(LeagueAcademyStanding::class.java)
            ?: listOf()
    }

    private fun exemptionRows(connection: Connection, season: String): List<LeagueExemption> = connection
        .query("SELECT * FROM $EXEMPTIONS_TABLE WHERE season = :season")
        .throwOnMappingFailure(false)
        .addParameter("season", season)
        .executeAndFetch(LeagueExemption::class.java)
        ?: listOf()

    /**
     * One row per member of the season's academy with their match counts, unranked and untotalled.
     *
     * Driven from the academy with LEFT JOINs, which is what keeps a member who has played nothing — and one who has
     * since left — in the standings with zeroes rather than absent. An INNER JOIN on the Discord profile would have been
     * the houses' choice, but here a missing profile would remove a player who has actually scored, so the name and the
     * avatar are allowed to be null and the website decides what to show.
     *
     * The inner `UNION ALL` flattens each match into one row per side, so a player is counted once for each match
     * whichever colour they had. **`UNION ALL` and not `UNION`**: the branches select no session column, so two sessions
     * with the same outcome for the same player produce identical rows, and `UNION` would dedupe them — quietly turning
     * two played matches into one. That is the kind of undercount nobody notices until a player disputes their total.
     *
     * A result that designates neither player counts as played and as neither won nor lost, which is why the three
     * counts are three independent CASEs rather than one.
     */
    private fun tallies(connection: Connection, season: String): List<LeagueTally> {
        val sql = "SELECT m.discord_id, d.discord_name, d.discord_avatar, h.house_id, m.active, " +
                " COALESCE(t.played, 0) AS played, COALESCE(t.won, 0) AS won, COALESCE(t.lost, 0) AS lost " +
                " FROM $MEMBERS_TABLE m " +
                " LEFT JOIN $DISCORD_TABLE d ON d.discord_id = m.discord_id " +
                " LEFT JOIN $HOUSE_MEMBERS_TABLE h ON h.discord_id = m.discord_id " +
                " LEFT JOIN ( " +
                "   SELECT s.discord_id, " +
                "          SUM(s.played) AS played, SUM(s.won) AS won, SUM(s.lost) AS lost " +
                "   FROM ( " +
                "     SELECT black_discord_id AS discord_id, " +
                "            CASE WHEN result IS NOT NULL AND result <> :unplayed THEN 1 ELSE 0 END AS played, " +
                "            CASE WHEN result = :blackWins THEN 1 ELSE 0 END AS won, " +
                "            CASE WHEN result = :whiteWins THEN 1 ELSE 0 END AS lost " +
                "     FROM $MATCHES_TABLE WHERE season = :season " +
                "     UNION ALL " +
                "     SELECT white_discord_id AS discord_id, " +
                "            CASE WHEN result IS NOT NULL AND result <> :unplayed THEN 1 ELSE 0 END AS played, " +
                "            CASE WHEN result = :whiteWins THEN 1 ELSE 0 END AS won, " +
                "            CASE WHEN result = :blackWins THEN 1 ELSE 0 END AS lost " +
                "     FROM $MATCHES_TABLE WHERE season = :season " +
                "   ) s GROUP BY s.discord_id " +
                " ) t ON t.discord_id = m.discord_id " +
                " WHERE m.season = :season "

        return connection
            .query(sql)
            .throwOnMappingFailure(false)
            .addParameter("unplayed", LeagueMatch.UNPLAYED)
            .addParameter("blackWins", LeagueMatch.BLACK_WINS)
            .addParameter("whiteWins", LeagueMatch.WHITE_WINS)
            .addParameter("season", season)
            .executeAndFetch(LeagueTally::class.java)
            ?: listOf()
    }

    /**
     * Sorts best first and stamps a competition rank: equal totals share a rank and the next one skips, so two players
     * on 40 share 2nd and the next is 4th. Ties break on name, to keep the list stable between calls.
     *
     * Active players come before inactive ones at equal renown, so the standings read as a live table rather than
     * interleaving people who have left. They still share the rank their total earns them — leaving does not demote
     * anybody, it only moves them down a tie.
     */
    private fun List<LeagueStanding>.withRanks(): List<LeagueStanding> {
        var lastTotal = -1
        var lastRank = 0
        return this
            .sortedWith(
                compareByDescending<LeagueStanding> { it.renown.total() }
                    .thenByDescending { it.active }
                    .thenBy { it.discordName ?: it.discordId }
            )
            .mapIndexed { index, standing ->
                if (standing.renown.total() != lastTotal) {
                    lastRank = index + 1
                    lastTotal = standing.renown.total()
                }
                standing.copy(rank = lastRank)
            }
    }
}
