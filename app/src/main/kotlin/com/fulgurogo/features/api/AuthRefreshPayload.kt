package com.fulgurogo.features.api

import com.fulgurogo.common.config.Config
import okhttp3.FormBody

data class AuthRefreshPayload(
    val clientId: String = Config.get("ladder.discord.auth.client.id"),
    val clientSecret: String = Config.get("ladder.discord.auth.client.secret"),
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
