package com.fulgurogo.api.db.model

/**
 * Body of `POST /gold/api/admin/house/points`: a signed number of house points for [discordId], and why.
 *
 * Every field nullable because Gson does not honour Kotlin nullability; the route validates them by hand.
 */
data class HouseAdjustmentRequestBody(
    val discordId: String?,
    val points: Int?,
    val reason: String?
)
