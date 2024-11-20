package com.fulgurogo.features.user.ogs

import com.fulgurogo.features.user.ServerUser
import com.fulgurogo.utilities.rankToString
import com.fulgurogo.utilities.toRank
import com.google.gson.annotations.SerializedName

data class OgsUser(
    val id: Int = 0,
    @SerializedName("ranking") val currentRanking: Double = 0.0,
    @SerializedName("ratings") val fullRatings: OgsRatings,
    var username: String? = "",
    @SerializedName("ui_class") val uiClass: String = ""
) : ServerUser() {
    data class OgsRatings(
        @SerializedName("overall") val overall: Rating = Rating(),
        @SerializedName("live-19x19") val live19: Rating = Rating()
    )

    data class Rating(
        val rating: Double? = null,
        val deviation: Double? = null,
        val volatility: Double? = null
    )

    fun isBot(): Boolean = uiClass == "bot"
    fun rankString(): String = fullRatings.overall.rating?.toRank()?.rankToString(false) ?: ""
    fun hasStableRank(): Boolean = uiClass != "provisional"

    override fun id(): String = id.toString()
    override fun pseudo(): String? = username
    override fun rank(): String = currentRanking.rankToString(false)
    override fun link(withRank: Boolean): String =
        "[$username${if (withRank) rank() else ""}](https://online-go.com/player/$id)"
}
