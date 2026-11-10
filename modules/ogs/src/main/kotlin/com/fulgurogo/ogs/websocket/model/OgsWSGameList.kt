package com.fulgurogo.ogs.websocket.model

import com.google.gson.annotations.SerializedName

data class OgsWSGameList(
    val size: Int = 0,
    val results: List<OgsWsGameLight> = listOf()
)

/**
 * One entry of a `gamelist/query` answer.
 *
 * Everything past the id, the two players and the board is only read by `GET /gold/api/games/live`, and nullable on
 * purpose: this class has no no-arg constructor, so Gson allocates it without running the defaults and a field OGS
 * leaves out comes back null whatever its declared type says.
 */
data class OgsWsGameLight(
    val id: Int = 0,
    val black: OgsWsGameLightPlayer,
    val white: OgsWsGameLightPlayer,
    val width: Int = 0,
    val height: Int = 0,
    val rengo: Boolean = false,
    val name: String? = null,
    @SerializedName("move_number") val moveNumber: Int? = null,
    val ranked: Boolean? = null,
    val handicap: Int? = null,
)

data class OgsWsGameLightPlayer(
    val id: Int = 0,
    val username: String? = null,
    /** OGS's continuous rank, 30 = 1d, as in [OgsWsUser]. */
    val rank: Double? = null,
)
