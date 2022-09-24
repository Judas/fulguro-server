package com.fulgurogo.features.user

import java.util.*

abstract class UserAccountGame {
    abstract fun date(): Date
    abstract fun server(): String
    abstract fun account(): UserAccount
    abstract fun gameServerId(): String

    abstract fun mainPlayerAccountId(): String
    abstract fun mainPlayerRank(): String
    abstract fun opponentAccountId(): String
    abstract fun opponentRank(): String
    abstract fun opponentPseudo(): String

    abstract fun isBlack(): Boolean
    abstract fun isWin(): Boolean
    abstract fun isLoss(): Boolean
    abstract fun handicap(): Int
    abstract fun komi(): Double
    abstract fun isLongGame(): Boolean
    abstract fun isFinished(): Boolean
}
