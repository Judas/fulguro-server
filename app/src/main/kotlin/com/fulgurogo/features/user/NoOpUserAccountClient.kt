package com.fulgurogo.features.user

import java.util.*

class NoOpUserAccountClient : UserAccountClient {
    override fun user(user: User): ServerUser? = null
    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> = listOf()
    override fun userGame(user: User, gameServerId: String): UserAccountGame? = null
}
