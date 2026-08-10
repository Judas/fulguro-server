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
     * The result, written by OGS. ⚠ Typed `String?` on purpose: the spec types these three as strings, which is doubtful
     * for the two `*_lost`, and the real type has never been seen on a finished match — nothing has finished yet. Gson
     * reads a JSON boolean or number into a String field without complaining, so a surprise here degrades to an odd
     * string rather than to a parse failure that would lose the whole tick. Revisit once a real match has ended.
     */
    val outcome: String? = null,
    @SerializedName("black_lost") val blackLost: String? = null,
    @SerializedName("white_lost") val whiteLost: String? = null,

    /**
     * Annulment, reported by OGS. This is what lets the league know a game was voided without depending on the game
     * ingestion — `OgsRealTimeService` has no notion of it at all.
     */
    val annulled: Boolean? = null,
    @SerializedName("moderator_annulled") val moderatorAnnulled: Boolean? = null,
    @SerializedName("annulment_reason") val annulmentReason: String? = null,

    @SerializedName("rating_complete") val ratingComplete: Boolean? = null,
    @SerializedName("black_member_rating") val blackMemberRating: Int? = null,
    @SerializedName("white_member_rating") val whiteMemberRating: Int? = null
) {
    /** True when OGS says the game behind this match was voided, by a moderator or otherwise. */
    fun isAnnulled(): Boolean = annulled == true || moderatorAnnulled == true

    /**
     * Which side lost, as a pair of flags, or null when OGS has not decided.
     *
     * Reads the two `*_lost` fields through their string form, tolerating `"true"`, `"True"` and `"1"`, since their real
     * type is unknown. Returns null unless exactly one side lost, so a finished game that designates neither — or both —
     * comes back as "no winner" rather than as an arbitrary one.
     */
    fun loser(): LeagueLoser? {
        val black = blackLost.toFlag()
        val white = whiteLost.toFlag()
        return when {
            black == true && white != true -> LeagueLoser.BLACK
            white == true && black != true -> LeagueLoser.WHITE
            else -> null
        }
    }

    private fun String?.toFlag(): Boolean? = when (this?.trim()?.lowercase()) {
        null, "", "null" -> null
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

/** Which side lost a finished match, when OGS designates one. */
enum class LeagueLoser { BLACK, WHITE }
