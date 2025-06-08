package com.fulgurogo.ogs.websocket.model

import com.fulgurogo.common.config.Config
import com.google.gson.annotations.SerializedName
import java.util.*

data class AuthRequest(
    val jwt: String,
    @SerializedName("device_id") val deviceId: String = UUID.randomUUID().toString(),
    @SerializedName("user_agent") val userAgent: String = Config.get("user.agent"),
)
