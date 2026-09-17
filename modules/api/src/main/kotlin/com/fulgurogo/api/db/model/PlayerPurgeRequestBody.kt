package com.fulgurogo.api.db.model

/** The player an administrator intends to remove from the ladder, after a ban. */
data class PlayerPurgeRequestBody(
    val discordId: String?,
)
