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
    /** OGS's own match id, an int. Indexed for lookups; results come from the sweep, there is no callback any more. */
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
     * - the winner OGS names — [BLACK_WINS], [WHITE_WINS], or [ANNULLED] which designates neither — once played;
     * - [UNPLAYED] as soon as the session was settled without a result arriving. Terminal: no write looks at it again,
     *   which is what makes a game played late on OGS have no effect on the league.
     *
     * The settlement leaves no match at null, which closes the nastiest failure mode here: a match pending forever is
     * neither played nor exempted, so it silently costs both players the perfect-attendance bonus, and that only
     * surfaces in May.
     */
    val result: String? = null,
    val created: Date,
    val finished: Date? = null,
    /**
     * An administrator's ruling on the match, one [LeagueAward] name per side: both null, or both set.
     *
     * It **overlays** [result] rather than replacing it. The sweep and the settlement go on writing [result] as if nothing
     * had happened, everything that scores reads the awards first, and clearing them brings back whatever OGS or the
     * settlement said. That is what makes a ruling undoable, and what keeps the tick from ever overwriting one.
     *
     * It touches renown only. House points and FGC come from `ogs_games`, so a [LeagueAward.WINNER] on a game that was
     * never played earns no house points — there is no game for the houses to see.
     */
    val blackAward: String? = null,
    val whiteAward: String? = null,
    /** When the ruling was made and by which administrator's Discord id — the only trace of it besides a log line. */
    val adjudicated: Date? = null,
    val adjudicatedBy: String? = null
) {
    /** Whether an administrator has ruled on this match, in which case the awards decide and [result] does not. */
    fun isAdjudicated(): Boolean = blackAward != null && whiteAward != null

    /** The award [discordId] got, or null when the match is not adjudicated or they are not in it. */
    fun awardOf(discordId: String): LeagueAward? = when (discordId) {
        blackDiscordId -> LeagueAward.of(blackAward)
        whiteDiscordId -> LeagueAward.of(whiteAward)
        else -> null
    }

    /**
     * Whether this match counts as played **by its result**: it has one, and it is not the settlement's. Blind to a
     * ruling, which can make the answer differ per player — [awardOf] is the question to ask when [isAdjudicated].
     *
     * A finished game whose result designates neither player is still played — 2 points to both, and the session counts
     * for the perfect-attendance bonus. The settings make that unreachable — japanese rules put komi at 6.5, measured, so no score can be level —
     * but the branch costs nothing and is the least surprising behaviour if they ever change.
     */
    fun isPlayed(): Boolean = result != null && result != UNPLAYED

    /** Whether the settlement closed this match unplayed. Terminal state. */
    fun isUnplayed(): Boolean = result == UNPLAYED

    /**
     * The Discord id that won, or null when nobody did — including when nothing is set yet. A ruling decides when there is
     * one, and only then does [result] get a say.
     */
    fun winner(): String? = if (isAdjudicated()) when {
        blackAward == LeagueAward.WINNER.name -> blackDiscordId
        whiteAward == LeagueAward.WINNER.name -> whiteDiscordId
        else -> null
    } else when (result) {
        BLACK_WINS -> blackDiscordId
        WHITE_WINS -> whiteDiscordId
        else -> null
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

        /**
         * A match OGS voided. **Played, and won by nobody** — so 2 points to each player and the session counts towards
         * the perfect-attendance bonus, which is the plan's rule that an annulled match is not a victory.
         *
         * Ours rather than OGS's, which says `outcome: "Cancellation"` and, worse, still fills `black_lost` — see
         * [loser]. Distinct from [UNPLAYED]: nobody failed to turn up, the game simply does not stand.
         */
        const val ANNULLED = "annulled"

        /**
         * Finished, not voided, and naming neither side. Also played with no winner, but for an honest reason rather than
         * an annulment — writing [ANNULLED] here would put a claim in the data that is not true.
         *
         * Unreachable as things stand: japanese rules put komi at a half point, so no score can be level. The same word
         * `ogs_games.result` already uses for a drawn game, so the two tables read alike if it ever becomes reachable.
         */
        const val JIGO = "jigo"
    }
}
