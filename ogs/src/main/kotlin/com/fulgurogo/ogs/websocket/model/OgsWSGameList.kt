package com.fulgurogo.ogs.websocket.model

data class OgsWSGameList(
    val size: Int = 0,
    val results: List<OgsWsGameLight> = listOf()
)

data class OgsWsGameLight(
    val id: Int = 0,
    val black: OgsWsGameLightPlayer,
    val white: OgsWsGameLightPlayer,
    val width: Int = 0,
    val height: Int = 0,
    val rengo: Boolean = false,
)

data class OgsWsGameLightPlayer(val id: Int = 0)
