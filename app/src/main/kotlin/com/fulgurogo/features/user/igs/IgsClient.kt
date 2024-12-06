package com.fulgurogo.features.user.igs

import com.fulgurogo.common.config.Config
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.ApiException
import com.fulgurogo.utilities.EmptyUserIdException
import com.fulgurogo.common.logger.Logger.Level.ERROR
import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import java.util.*

class IgsClient : UserAccountClient {
    companion object {
        private const val LOGIN_PROMPT = "Login: "
        private const val PASSWORD_PROMPT = "1 1"
        private const val DEFAULT_PROMPT = "1 5"
        private const val UNKNOWN_PLAYER = "5 Cannot find player."
    }

    private val igsTelnetClient = IgsTelnetClient()

    override fun user(user: User): IgsUser? = user(user.igsId)
    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> = listOf()
    override fun userGame(user: User, gameServerId: String): UserAccountGame? = null

    fun user(id: String?): IgsUser? = try {
        if (id.isNullOrBlank())
            throw EmptyUserIdException
        else {
            igsTelnetClient.connect(Config.get("igs.server.host"), Config.get("igs.server.port").toInt())
            igsTelnetClient.readUntil(LOGIN_PROMPT)
            igsTelnetClient.write(Config.get("igs.user.name"))
            igsTelnetClient.readUntil(PASSWORD_PROMPT)
            igsTelnetClient.write(Config.get("igs.user.password"))
            igsTelnetClient.readUntil(DEFAULT_PROMPT)
            igsTelnetClient.write("stats $id")
            val playerInfo = igsTelnetClient.readUntil(DEFAULT_PROMPT)
            igsTelnetClient.disconnect()
            if (playerInfo.contains(UNKNOWN_PLAYER)) {
                val error = ApiException(UNKNOWN_PLAYER)
                log(ERROR, error.message!!, error)
                throw error
            } else {
                log(INFO, "user SUCCESS")
                IgsUser(playerInfo)
            }
        }
    } catch (e: Exception) {
        null
    }
}
