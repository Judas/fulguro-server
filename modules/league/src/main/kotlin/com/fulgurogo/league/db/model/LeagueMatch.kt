package com.fulgurogo.league.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

/** Which side of a match is being talked about. Used where a column exists once per colour. */
enum class LeagueSide { BLACK, WHITE }

/**
 * One pairing: two players, the challenge OGS created for them, and how it ended.
 *
 * This table is the only source of the renown — there is no league points register. A match carries its two players,
 * their house frozen at draw time and its result, so everything the standings show is derivable from it, and the
 * primary key already provides the idempotence a register would have been asked to provide.
 *
 * The key is `(season, session, blackDiscordId)` with a unique key on the white side, which together state the real rule
 * of the domain: **at most one match per player per session, whichever colour they have**. That is what makes a draw run
 * twice harmless.
 */
@GenerateNoArgConstructor
data class LeagueMatch(
    val season: String,
    val session: Int,
    val blackDiscordId: String,
    val whiteDiscordId: String,
    /**
     * The houses, frozen when the row is written, for the same reason as in `house_points`: an academy's total must not
     * move when a player changes house or leaves it.
     */
    val blackHouseId: Int,
    val whiteHouseId: Int,
    /** What the pairing cost the draw, kept so a surprising pairing can be explained months later. */
    val pairingScore: Double,
    /**
     * What we send OGS, and the key OGS keys its own idempotence on: the same value returns the same match, links
     * included, rather than creating a second one.
     *
     * Prefixed with the database name, because dev and prod share the single OGS league — without that prefix, two
     * draws pairing the same players on the same session would send the same id to the same league.
     */
    val leagueMatchId: String,
    /** OGS's own match id, an int. The callback arrives on this, which is why the column is indexed. */
    val ogsMatchId: Int? = null,
    /** The two player invitations — secrets, sent by DM — and the spectator link, which is the only publishable one. */
    val blackInvite: String? = null,
    val whiteInvite: String? = null,
    val spectatorLink: String? = null,
    /**
     * When each player's DM went out. Never cleared, and neither are the links: together they answer "who did not get
     * their link?" without rereading the logs, and the resend is done by hand, possibly days later.
     */
    val blackNotified: Date? = null,
    val whiteNotified: Date? = null,
    /** The OGS game that came out of the challenge, once it exists, and the gold id the rest of the app knows it by. */
    val ogsGameId: Int? = null,
    val goldId: String? = null,
    /**
     * Three families of values, and this is where the "not played, not replayable" rule lives:
     *
     * - **null** while the fate of the match is still open;
     * - the winner OGS names — [BLACK_WINS], [WHITE_WINS], or something else that designates neither — once played;
     * - [UNPLAYED] as soon as the session was settled without a result arriving. Terminal: no write looks at it again,
     *   which is what makes a game played late on OGS have no effect on the league.
     *
     * The settlement leaves no match at null, which closes the nastiest failure mode here: a match pending forever is
     * neither played nor exempted, so it silently costs both players the perfect-attendance bonus, and that only
     * surfaces in May.
     */
    val result: String? = null,
    val created: Date,
    val finished: Date? = null
) {
    /**
     * Whether this match counts as played: it has a result, and that result is not the settlement's.
     *
     * A finished game whose result designates neither player is still played — 2 points to both, and the session counts
     * for the perfect-attendance bonus. The settings make that unreachable (japanese rules put komi at 7.5, so no jigo),
     * but the branch costs nothing and is the least surprising behaviour if they ever change.
     */
    fun isPlayed(): Boolean = result != null && result != UNPLAYED

    /** Whether the settlement closed this match unplayed. Terminal state. */
    fun isUnplayed(): Boolean = result == UNPLAYED

    /** The Discord id the result designates, or null when it designates neither — including when nothing is set yet. */
    fun winner(): String? = when (result) {
        BLACK_WINS -> blackDiscordId
        WHITE_WINS -> whiteDiscordId
        else -> null
    }

    /** The Discord id on one side, for the callers that hold a [LeagueSide] rather than a colour. */
    fun playerOn(side: LeagueSide): String = when (side) {
        LeagueSide.BLACK -> blackDiscordId
        LeagueSide.WHITE -> whiteDiscordId
    }

    companion object {
        /**
         * The value the settlement writes, and the one value of `result` that is ours rather than OGS's.
         *
         * Kept here, next to [isUnplayed], because the accessor writes it in SQL and the model reads it back: two
         * spellings of it would make the settlement invisible to [isPlayed], which would then count a void match as
         * played and hand out 2 points for a game nobody played.
         */
        const val UNPLAYED = "unplayed"

        /** How the platforms spell a win, the same two strings `ogs_games.result` and `kgs_games.result` carry. */
        const val BLACK_WINS = "black"
        const val WHITE_WINS = "white"
    }
}
