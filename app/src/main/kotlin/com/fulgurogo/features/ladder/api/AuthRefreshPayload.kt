package com.fulgurogo.features.ladder.api

import com.fulgurogo.Config
import okhttp3.FormBody

data class AuthRefreshPayload(
    val clientId: String = Config.Ladder.DISCORD_AUTH_CLIENT_ID,
    val clientSecret: String = Config.Ladder.DISCORD_AUTH_CLIENT_SECRET,
    val grantType: String = "refresh_token",
    val refreshToken: String
) {
    fun toFormBody() = FormBody.Builder()
        .add("client_id", clientId)
        .add("client_secret", clientSecret)
        .add("grant_type", grantType)
        .add("refresh_token", refreshToken)
        .build()
}
