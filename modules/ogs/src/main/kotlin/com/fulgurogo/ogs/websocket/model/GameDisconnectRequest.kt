package com.fulgurogo.ogs.websocket.model

import com.google.gson.annotations.SerializedName

data class GameDisconnectRequest(
    @SerializedName("game_id") val gameId: Int = 0
)
