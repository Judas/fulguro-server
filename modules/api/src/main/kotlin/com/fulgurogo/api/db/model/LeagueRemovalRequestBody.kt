package com.fulgurogo.api.db.model

/** Body of `POST /gold/api/admin/league/remove`: the member an administrator takes out of the current season's league. */
data class LeagueRemovalRequestBody(
    val discordId: String?
)
