package com.fulgurogo.features.api

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class AuthCredentials(
    val goldId: String,
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String,
    val expirationDate: Date
)
