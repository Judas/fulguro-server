package com.fulgurogo.features.user

import com.fulgurogo.features.user.kgs.KgsGame
import java.util.*

data class UserAccountLiveGame(
    val date: Date,
    val server: String,
    val serverId: String,
    val blackPlayerServerId: String,
    val whitePlayerServerId: String,
    val sgfLink: String?,
    val roomName: String?
) {
    companion object {
        fun from(game: KgsGame, roomMap: MutableMap<Int, String>) = UserAccountLiveGame(
            date = game.date(),
            server = game.server(),
            serverId = game.serverId(),
            blackPlayerServerId = game.blackPlayerServerId(),
            whitePlayerServerId = game.whitePlayerServerId(),
            sgfLink = null,
            roomName = roomMap[game.roomId]
        )
    }
}
