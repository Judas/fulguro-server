package com.fulgurogo.league.ogs.model

import com.google.gson.annotations.SerializedName

/**
 * A match as the OGS online-league API returns it.
 *
 * **One model for all three calls.** `POST /matches/`, `GET /matches/` and `GET /matches/{id}` were measured to return
 * exactly the same object, field for field, so there is nothing to gain from three shapes that would have to stay in
 * agreement.
 *
 * Everything except the handful of fields we send is written by OGS and read-only to us. Every field is nullable or
 * defaulted, because the object is returned in three very different states — freshly created with no game behind it, in
 * progress, and finished — and Gson leaves anything absent at its default rather than failing.
 *
 * See `doc/ogs-online-league-api.md` for the contract and the probe that established it.
 */
data class OgsLeagueMatch(
    /** OGS's own id, an **int**, and the one the end-of-game callback carries. */
    val id: Int = 0,
    /** The id we chose, and the key OGS keys its idempotence on. */
    @SerializedName("league_match_id") val leagueMatchId: String? = null,
    val name: String? = null,
    @SerializedName("black_member_id") val blackMemberId: String? = null,
    @SerializedName("white_member_id") val whiteMemberId: String? = null,
    /**
     * The two player invitations, each carrying a 22-character key. **Secrets**: whoever opens one plays that side, so
     * they go out by DM and never into a log line, an API response or a page.
     */
    @SerializedName("black_invite") val blackInvite: String? = null,
    @SerializedName("white_invite") val whiteInvite: String? = null,
    /** Contains only the match id, so this is the one link that can be published. */
    @SerializedName("spectator_link") val spectatorLink: String? = null,
    val league: String? = null,

    // The settings we send, echoed back. Read only to confirm OGS took them.
    val rules: String? = null,
    val handicap: Int? = null,
    val height: Int? = null,
    val width: Int? = null,
    @SerializedName("time_control") val timeControl: String? = null,
    @SerializedName("main_time") val mainTime: Int? = null,
    val periods: Int? = null,
    @SerializedName("period_time") val periodTime: Int? = null,
    @SerializedName("stones_per_period") val stonesPerPeriod: Int? = null,
    @SerializedName("time_increment") val timeIncrement: Int? = null,
    @SerializedName("initial_time") val initialTime: Int? = null,
    @SerializedName("max_time") val maxTime: Int? = null,
    @SerializedName("per_move") val perMove: Int? = null,
    @SerializedName("total_time") val totalTime: Int? = null,

    /**
     * The OGS game id once both players have accepted, null before. This is the bridge to `ogs_games` and therefore to
     * the rest of the application: `gold_id` is `OGS_<game>`.
     */
    val game: Int? = null,
    val started: Boolean? = null,
    val finished: Boolean? = null,
    val cancelled: Boolean? = null,

    /**
     * The result, written by OGS, and now measured on a finished match rather than guessed.
     *
     * [outcome] really is a string, and a human-readable one — `"Cancellation"` on the annulled match of 11 August. The
     * two `*_lost` are real **booleans**, despite the spec typing them as strings.
     */
    val outcome: String? = null,
    @SerializedName("black_lost") val blackLost: Boolean? = null,
    @SerializedName("white_lost") val whiteLost: Boolean? = null,

    /**
     * Annulment, reported by OGS. This is what lets the league know a game was voided without depending on the game
     * ingestion — `OgsRealTimeService` has no notion of it at all.
     */
    val annulled: Boolean? = null,
    @SerializedName("moderator_annulled") val moderatorAnnulled: Boolean? = null,
    @SerializedName("annulment_reason") val annulmentReason: String? = null,

    @SerializedName("rating_complete") val ratingComplete: Boolean? = null,
    /**
     * The league ratings OGS keeps, and they are **doubles** — Glicko, not integers.
     *
     * ⚠ These were `Int?` until measured, and that was a crash waiting for a rating that is not a round number. Gson
     * reads `1500.0` into an `Int` happily and throws on `1523.7`, and the throw loses **the whole object**, not just the
     * field. Since the client answers null or an empty list on failure, one fractional rating would have made a sweep
     * come back empty and write nothing, silently, for as long as it stayed fractional.
     */
    @SerializedName("black_member_rating") val blackMemberRating: Double? = null,
    @SerializedName("white_member_rating") val whiteMemberRating: Double? = null
) {
    /**
     * True when OGS says the game behind this match was voided, by a moderator or otherwise.
     *
     * ⚠ Measured on the annulled match of 11 August: `annulled` was `true` while `moderator_annulled` and
     * `annulment_reason` were both **null**, and the match-level `cancelled` was **false**. So `annulled` alone is the
     * signal — `cancelled` is about something else, and there is no reason to report even when there is an annulment.
     */
    fun isAnnulled(): Boolean = annulled == true || moderatorAnnulled == true

    /**
     * The side that lost, or null when the match designates no loser — **including when it was annulled**.
     *
     * ⚠ **This is the trap, and it is not hypothetical.** An annulled match still carries a winner: on 11 August the
     * voided match came back `finished: true`, `annulled: true`, `outcome: "Cancellation"`, and
     * `black_lost: true, white_lost: false`. Read the two flags on their own and a game that never counted becomes a win
     * for white. The annulment check therefore lives **here**, at the only place those flags are read, rather than being
     * left as something every caller must remember: a league match that was annulled is not a victory, and this function
     * is where that rule is enforced.
     *
     * [isAnnulled] stays available for the caller that wants to tell "void" from "finished with no winner" — worth a
     * different log line, and the two are worth telling apart when a player asks.
     *
     * Null too unless **exactly** one side lost, which also covers the in-progress state: a running game reports both
     * `*_lost` as true on the games API, and the same shape reaching a match must not name a loser either.
     */
    fun loser(): LeagueLoser? = when {
        isAnnulled() -> null
        blackLost == true && whiteLost != true -> LeagueLoser.BLACK
        whiteLost == true && blackLost != true -> LeagueLoser.WHITE
        else -> null
    }
}

/** Which side lost a finished match, when OGS designates one. */
enum class LeagueLoser { BLACK, WHITE }
