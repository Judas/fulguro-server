package com.fulgurogo.ogs.websocket.model

sealed class OgsWsMessage {
    class GameList(val data: OgsWSGameList) : OgsWsMessage()
    class GameData(val data: OgsWsGameData) : OgsWsMessage()
}
