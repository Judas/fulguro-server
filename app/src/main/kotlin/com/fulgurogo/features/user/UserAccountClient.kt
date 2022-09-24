package com.fulgurogo.features.user

import java.util.*

interface UserAccountClient {
    fun user(user: User): ServerUser?
    fun userGames(user: User, from: Date, to: Date): List<UserAccountGame>
    fun userGame(user: User, gameId: String): UserAccountGame?
}
