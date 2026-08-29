package com.fulgurogo.api.db.model

/** The exact association an administrator saw and intends to remove. */
data class AccountUnlinkRequestBody(
    val discordId: String?,
    val account: String?,
    val accountId: String?,
)
