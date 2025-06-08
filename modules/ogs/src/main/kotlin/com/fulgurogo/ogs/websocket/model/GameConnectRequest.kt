package com.fulgurogo.ogs.websocket.model

import com.google.gson.annotations.SerializedName

data class GameConnectRequest(
    @SerializedName("game_id") val gameId: Int = 0,
    val chat: Boolean = false
)
