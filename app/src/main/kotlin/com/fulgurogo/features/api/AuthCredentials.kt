package com.fulgurogo.features.api

import com.fulgurogo.utilities.NoArg
import java.util.*

@NoArg
data class AuthCredentials(
    val goldId: String,
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String,
    val expirationDate: Date
)
