package com.fulgurogo.features.user.ffg

import com.fulgurogo.TAG
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.ApiException
import com.fulgurogo.utilities.EmptyUserIdException
import com.fulgurogo.utilities.InvalidUserException
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*

class FfgClient : UserAccountClient {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .build()

    override fun user(user: User): FfgUser? = user(user.ffgId)
    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> = listOf()
    override fun userGame(user: User, gameServerId: String): UserAccountGame? = null

    fun user(id: String?): FfgUser? = try {
        if (id.isNullOrBlank())
            throw EmptyUserIdException
        else {
            val route = "${Config.get("ffg.website.url")}/php/affichePersonne.php?id=$id"
            val request: Request = Request.Builder().url(route).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                log(TAG, "user SUCCESS ${response.code}")
                try {
                    val user = FfgUser(id, response.body!!.string())
                    response.close()

                    if (user.name == null) {
                        val error = ApiException("user FAILURE : No user name")
                        log(TAG, error.message!!, error)
                        null
                    } else user
                } catch (e: InvalidUserException) {
                    log(TAG, "user FAILURE", e)
                    throw e
                }
            } else {
                val error = ApiException("user FAILURE " + response.code)
                log(TAG, error.message!!, error)
                throw error
            }
        }
    } catch (e: Exception) {
        null
    }
}
