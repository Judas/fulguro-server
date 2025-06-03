package com.fulgurogo.ogs.api.model

import com.google.gson.annotations.SerializedName

data class OgsApiUser(
    val id: Int = 0,
    val ranking: Double = 0.0,
    var username: String? = "",
    @SerializedName("ui_class") val uiClass: String = ""
) {
    fun isBot(): Boolean = uiClass == "bot"
}
