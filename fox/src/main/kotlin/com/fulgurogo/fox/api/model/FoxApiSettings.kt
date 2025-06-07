package com.fulgurogo.fox.api.model

import com.google.gson.annotations.SerializedName

data class FoxApiSettings(
    val handicap: Int = 0,
    val komi: Double = 0.0,
    @SerializedName("board_size") val size: Int = 0
)
