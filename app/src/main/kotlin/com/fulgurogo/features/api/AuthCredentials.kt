package com.fulgurogo.features.api

import java.util.*

data class AuthCredentials(
    val goldId: String,
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String,
    val expirationDate: Date
)
