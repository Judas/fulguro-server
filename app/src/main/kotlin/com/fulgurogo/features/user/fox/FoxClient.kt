package com.fulgurogo.features.user.fox

import com.fulgurogo.Config
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.features.user.UserAccountLiveGame
import com.fulgurogo.utilities.ApiException
import com.fulgurogo.utilities.EmptyUserIdException
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import com.google.gson.Gson
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*

class FoxClient : UserAccountClient {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .followRedirects(true)
        .addInterceptor {
            // We need to change the Content-Type Header because fox is lying to us !
            val response: Response = it.proceed(it.request())
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val modifiedBody = response.body?.bytes()?.toResponseBody(mediaType)
            response.newBuilder()
                .removeHeader("Content-Type")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .body(modifiedBody)
                .build()
        }
        .build()

    override fun user(user: User): FoxUser? = user(user.foxPseudo)
    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> =
        lastHundredGames(user)
            .asSequence()
            .filter { it.date().after(from) }
            .filter { it.date().before(to) }
            .filter { it.isNineteen() }
            .sortedBy { it.gamestarttime }
            .toList()

    override fun userGame(user: User, gameServerId: String): UserAccountGame? =
        lastHundredGames(user).find { it.serverId() == gameServerId }

    override fun liveGames(): List<UserAccountLiveGame> = listOf()

    fun user(pseudo: String?): FoxUser? =
        if (pseudo.isNullOrBlank()) null
        else {
            val foxUser = get("${Config.Fox.API_URL}/${Config.Fox.USER_INFO}$pseudo", FoxUser::class.java)
            val hasPseudo = foxUser.pseudo().isNullOrBlank().not()
            if (hasPseudo) {
                val lastGame = lastHundredGames(foxUser.pseudo()!!).firstOrNull()
                foxUser.rank = when (foxUser.pseudo()) {
                    lastGame?.blackPlayerPseudo() -> lastGame?.blackPlayerRank()
                    lastGame?.whitePlayerPseudo() -> lastGame?.whitePlayerRank()
                    else -> null
                }
            }
            if (hasPseudo) foxUser else null
        }

    private fun lastHundredGames(user: User): List<FoxGame> =
        if (user.foxPseudo.isNullOrEmpty()) throw EmptyUserIdException
        else lastHundredGames(user.foxPseudo)

    private fun lastHundredGames(pseudo: String): List<FoxGame> {
        val gameRequest = get(
            "${Config.Fox.API_URL}/${Config.Fox.USER_GAMES}$pseudo",
            FoxGameList::class.java
        )

        val games = gameRequest.chesslist.toMutableList()
        log(INFO, "Found ${games.size} total games")
        return games
    }

    private fun <T : Any> get(route: String, className: Class<T>): T {
        val request: Request = Request.Builder().url(route).get().build()
        log(INFO, "GET REQUEST $route")
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            log(INFO, "GET SUCCESS ${response.code}")
            val apiResponse = gson.fromJson(response.body?.string(), className)
            response.close()
            return apiResponse
        } else {
            val error = ApiException("GET FAILURE " + response.code)
            log(ERROR, error.message!!, error)
            throw error
        }
    }
}
