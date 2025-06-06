package com.fulgurogo.ogs.api.model

import com.google.gson.annotations.SerializedName

data class OgsApiGamePlayers(
    val black: OgsApiUser,
    val white: OgsApiUser
)

data class OgsApiUser(
    val id: Int = 0,
    val ranking: Double = 0.0,
    val username: String = "",
    @SerializedName("ui_class") val uiClass: String = ""
) {
    fun isBot(): Boolean = uiClass == "bot"
}
