package com.fulgurogo.features.api

import com.fulgurogo.common.Config
import okhttp3.FormBody

data class AuthRequestPayload(
    val clientId: String = Config.get("ladder.discord.auth.client.id"),
    val clientSecret: String = Config.get("ladder.discord.auth.client.secret"),
    val grantType: String = "authorization_code",
    val code: String,
    val redirectUri: String = Config.get("ladder.discord.auth.redirect.uri")
) {
    fun toFormBody() = FormBody.Builder()
        .add("client_id", clientId)
        .add("client_secret", clientSecret)
        .add("grant_type", grantType)
        .add("code", code)
        .add("redirect_uri", redirectUri)
        .build()
}
