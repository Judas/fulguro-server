package com.fulgurogo.house

import com.fulgurogo.house.db.model.HouseGame
import com.fulgurogo.house.db.model.HousePoints

/**
 * The scale, and nothing else: no database, no clock, no logging. One game and one side in, one register row out.
 *
 * | Type            | Points | Condition                                     |
 * |-----------------|--------|-----------------------------------------------|
 * | `played`        | 1      | always                                        |
 * | `goldOpponent`  | 2      | the opponent has a known Discord id           |
 * | `rivalHouse`    | 2      | the opponent is a member of another house     |
 * | `longGame`      | 2      | the game is a long one                        |
 * | `victory`       | 2      | the result names this player                  |
 * | `evenGame`      | 1      | no handicap                                   |
 * | `ranked`        | 1      | the game is ranked                            |
 *
 * Every bonus adds up, without exception: 11 points at most, for winning a long, ranked, even game against a member of
 * another house.
 *
 * That sum is then **divided by the board**, and that division is the whole of the second half of the scale:
 *
 * | Board | Divisor | Best possible |
 * |-------|---------|---------------|
 * | 19×19 | 1       | 11            |
 * | 13×13 | 2       | 6             |
 * | 9×9   | 4       | 3             |
 *
 * Rounded **up**, so the smallest thing a scored game can be worth is 1 point and no game that entered the register is
 * ever worth nothing. Any other board is worth nothing at all — but by never being selected, not by scoring zero, see
 * [fromGame].
 */
object HousePointsCalculator {
    private const val PLAYED = 1
    private const val GOLD_OPPONENT = 2
    private const val RIVAL_HOUSE = 2
    private const val LONG_GAME = 2
    private const val VICTORY = 2
    private const val EVEN_GAME = 1
    private const val RANKED = 1

    /**
     * The boards the scale knows, and what a game on each is divided by. This is the same list the scanner's selection
     * filters on — `HouseDatabaseAccessor.gamesToScore` — and the two have to name the same sizes: the query decides
     * what is scored at all, this map decides what it is worth.
     */
    private val DIVISORS = mapOf(19 to 1, 13 to 2, 9 to 4)

    private const val BLACK_WINS = "black"
    private const val WHITE_WINS = "white"
    private const val UNFINISHED = "unfinished"

    /**
     * What [game] earns the player on the [black] side, or null when that side earns nothing at all — an unknown
     * account, a game with no result yet, or a board the scale does not know.
     *
     * [opponentHouseId] is the house of the *other* side, null when that player is in none or unknown to the server.
     * The season and the house are given rather than derived: both are frozen into the row by the caller, which is what
     * makes a house total immune to its members moving.
     *
     * [HousePoints.scoredAt] is deliberately left null. A pure function has no business reading the clock, and the
     * column is filled by `NOW()` in the insert.
     *
     * Three readings of the scale that are easy to get wrong:
     *
     * **`evenGame` means no handicap**, not a drawn game. Komi at 7.5 makes a true draw near impossible, so reading it
     * as `result = 'jigo'` gives a plausible scale where the bonus almost never fires. It is `handicap == 0`, and it
     * stacks with `victory` — winning an even game earns both.
     *
     * **`goldOpponent` means an opponent the server knows**, i.e. one who linked an account. No condition on their
     * rating: a rating can arrive later, and requiring one would make the same game worth two points more an hour
     * afterwards.
     *
     * **An opponent from the player's own house earns `goldOpponent` alone.** Bonuses stack, but `rivalHouse` wants a
     * different house.
     */
    fun fromGame(game: HouseGame, black: Boolean, season: String, houseId: Int, opponentHouseId: Int?): HousePoints? {
        // A game still in progress must not be scored. The `house_games` view already filters those out, so this only
        // ever fires if that changes -- but the (goldId, discordId) primary key makes a row permanent, so scoring an
        // unfinished game would freeze a missing victory bonus for good, in silence.
        if (game.result.equals(UNFINISHED, ignoreCase = true)) return null

        // A board nobody plays a real game on -- 5x5, 21x21, or a 0 out of an SGF with no SZ. The selection already
        // leaves those out, which is what "not counted" has to mean here: a zero-point row would still be a row, and
        // the primary key would make it permanent. Returning null cannot stall the scan the way it would for a game
        // the selection does return, precisely because such a game is never returned in the first place.
        val divisor = DIVISORS[game.size] ?: return null

        val discordId = (if (black) game.blackDiscordId else game.whiteDiscordId) ?: return null
        val opponentDiscordId = if (black) game.whiteDiscordId else game.blackDiscordId

        val opponentIsGold = opponentDiscordId != null
        val opponentIsRival = opponentIsGold && opponentHouseId != null && opponentHouseId != houseId
        val won = game.result.equals(if (black) BLACK_WINS else WHITE_WINS, ignoreCase = true)

        val played = PLAYED
        val goldOpponent = if (opponentIsGold) GOLD_OPPONENT else 0
        val rivalHouse = if (opponentIsRival) RIVAL_HOUSE else 0
        val longGame = if (game.longGame) LONG_GAME else 0
        val victory = if (won) VICTORY else 0
        val evenGame = if (game.handicap == 0) EVEN_GAME else 0
        val ranked = if (game.ranked) RANKED else 0
        val raw = played + goldOpponent + rivalHouse + longGame + victory + evenGame + ranked

        return HousePoints(
            goldId = game.goldId,
            discordId = discordId,
            houseId = houseId,
            season = season,
            played = played,
            goldOpponent = goldOpponent,
            rivalHouse = rivalHouse,
            longGame = longGame,
            victory = victory,
            evenGame = evenGame,
            ranked = ranked,
            // Integer arithmetic rather than kotlin.math.ceil: both operands are positive, so this is the ceiling
            // exactly, with no double in the middle of a figure that ends up in a ranking.
            total = (raw + divisor - 1) / divisor
        )
    }
}
