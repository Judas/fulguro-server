package com.fulgurogo.features.user

import com.fulgurogo.features.user.kgs.KgsGame

data class UserAccountLiveGame(
    val server: String,
    val blackPlayerServerId: String,
    val whitePlayerServerId: String,
    val sgfLink: String?,
    val roomName: String?
) {
    companion object {
        fun from(game: KgsGame, roomMap: MutableMap<Int, String>) = UserAccountLiveGame(
            server = game.server(),
            blackPlayerServerId = game.blackPlayerServerId(),
            whitePlayerServerId = game.whitePlayerServerId(),
            sgfLink = null,
            roomName = roomMap[game.roomId]
        )
    }
}
