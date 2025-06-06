package com.fulgurogo.ogs.websocket.model

import com.google.gson.annotations.SerializedName

data class GameListRequest(
    val list: String = "live",
    @SerializedName("sort_by") val sortBy: String = "rank",
    val where: Filters = Filters(),
    val from: Int = 0,
    val limit: Int = 300,
    val channel: String = ""
) {
    data class Filters(
        @SerializedName("hide_bot_games") val noBot: Boolean = true,
        val players: List<Int> = listOf()
    )

    companion object {
        const val GAME_LIST_REQUEST_ID = 1234
    }
}
