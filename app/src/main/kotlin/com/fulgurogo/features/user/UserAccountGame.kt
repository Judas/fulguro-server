package com.fulgurogo.features.user

import java.util.*

abstract class UserAccountGame {
    fun gameId(): String = "${server()}_${serverId()}"
    abstract fun date(): Date
    abstract fun server(): String
    abstract fun account(): UserAccount
    abstract fun serverId(): String

    abstract fun blackPlayerServerId(): String
    abstract fun blackPlayerPseudo(): String
    abstract fun blackPlayerRank(): String
    abstract fun blackPlayerWon(): Boolean

    abstract fun whitePlayerServerId(): String
    abstract fun whitePlayerPseudo(): String
    abstract fun whitePlayerRank(): String
    abstract fun whitePlayerWon(): Boolean

    abstract fun handicap(): Int
    abstract fun komi(): Double
    abstract fun isLongGame(): Boolean
    abstract fun isFinished(): Boolean

    abstract fun sgfLink(blackPlayerDiscordId: String, whitePlayerDiscordId: String): String?
}
