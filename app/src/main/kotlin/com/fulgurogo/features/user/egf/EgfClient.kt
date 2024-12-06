package com.fulgurogo.features.user.egf

import com.fulgurogo.common.config.Config
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.ApiException
import com.fulgurogo.utilities.EmptyUserIdException
import com.fulgurogo.utilities.InvalidUserException
import com.fulgurogo.common.logger.Logger.Level.ERROR
import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*

class EgfClient : UserAccountClient {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .build()

    override fun user(user: User): EgfUser? = user(user.egfId)
    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> = listOf()
    override fun userGame(user: User, gameServerId: String): UserAccountGame? = null

    fun user(id: String?): EgfUser? = try {
        if (id.isNullOrBlank())
            throw EmptyUserIdException
        else {
            val route = "${Config.get("egf.website.url")}?key=$id"
            val request: Request = Request.Builder().url(route).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                log(INFO, "user SUCCESS ${response.code}")
                try {
                    val user = EgfUser(id, response.body!!.string())
                    response.close()

                    if (user.name == null) {
                        val error = ApiException("user FAILURE : No user name")
                        log(ERROR, error.message!!, error)
                        null
                    } else user
                } catch (e: InvalidUserException) {
                    log(ERROR, "user FAILURE", e)
                    throw e
                }
            } else {
                val error = ApiException("user FAILURE " + response.code)
                log(ERROR, error.message!!, error)
                throw error
            }
        }
    } catch (e: Exception) {
        null
    }
}
