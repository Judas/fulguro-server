package com.fulgurogo.api.db.model

data class LinkRequestBody(
    val discordId: String,
    val account: String,
    val accountId: String
)
