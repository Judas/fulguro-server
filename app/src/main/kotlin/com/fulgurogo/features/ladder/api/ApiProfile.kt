package com.fulgurogo.features.ladder.api

import java.util.*

data class ApiProfile(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val expirationDate: Date
)
