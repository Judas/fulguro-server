package com.fulgurogo.features.api

data class LinkRequestBody(
    val discordId: String,
    val account: String,
    val accountId: String
)
