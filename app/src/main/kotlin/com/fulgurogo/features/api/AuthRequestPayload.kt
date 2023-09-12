package com.fulgurogo.features.api

import com.fulgurogo.Config
import okhttp3.FormBody

data class AuthRequestPayload(
    val clientId: String = Config.Ladder.DISCORD_AUTH_CLIENT_ID,
    val clientSecret: String = Config.Ladder.DISCORD_AUTH_CLIENT_SECRET,
    val grantType: String = "authorization_code",
    val code: String,
    val redirectUri: String = Config.Ladder.DISCORD_AUTH_REDIRECT_URI
) {
    fun toFormBody() = FormBody.Builder()
        .add("client_id", clientId)
        .add("client_secret", clientSecret)
        .add("grant_type", grantType)
        .add("code", code)
        .add("redirect_uri", redirectUri)
        .build()
}
