package com.fulgurogo.features.ladder.api

import com.fulgurogo.utilities.toDate
import java.time.ZonedDateTime
import java.util.*

data class AuthRequestResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long,
    val refresh_token: String,
    val scope: String
)
