package com.fulgurogo.league.ogs.model

import com.google.gson.annotations.SerializedName

/**
 * The body of `POST /matches/`: the two players, our own id for the match, and **every game setting**.
 *
 * The settings are ours, match by match, not a league-wide configuration — the wiki documents only `handicap` and
 * suggests otherwise. They are filled from the constants in `OgsLeagueClient`, which is where the reason for each value
 * is written down; none of them is a preference.
 *
 * Only the fields of the chosen time control are sent. The others (`stones_per_period`, `time_increment`, …) belong to
 * `canadian`, `fischer`, `simple` and `absolute`, and OGS validates them against `time_control` — a `byoyomi` without
 * `periods` is a 400, measured.
 */
data class OgsLeagueMatchRequest(
    @SerializedName("black_member_id") val blackMemberId: String,
    @SerializedName("white_member_id") val whiteMemberId: String,
    @SerializedName("league_match_id") val leagueMatchId: String,
    val rules: String,
    val handicap: Int,
    val height: Int,
    val width: Int,
    @SerializedName("time_control") val timeControl: String,
    @SerializedName("main_time") val mainTime: Int,
    val periods: Int,
    @SerializedName("period_time") val periodTime: Int
)

/** The body of `PUT /member/{member_id}`: one required field, and we tell it nothing meaningful. See the client. */
data class OgsLeagueMemberRequest(val rating: Int)
