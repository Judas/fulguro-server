package com.fulgurogo.features.api

data class AuthRequestResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long,
    val refresh_token: String,
    val scope: String
)
