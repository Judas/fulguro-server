package com.fulgurogo.features.user.ogs

import com.fulgurogo.Config
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.ApiException
import com.fulgurogo.utilities.DATE_ZONE
import com.fulgurogo.utilities.EmptyUserIdException
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
import com.fulgurogo.utilities.log
import com.google.gson.Gson
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime
import java.util.*

class OgsClient : UserAccountClient {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .followRedirects(true)
        .build()
    private var lastCallTime: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

    override fun user(user: User): OgsUser? = user(user.ogsId)

    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> =
        allGamesSince(user, from)
            .asSequence()
            .filter { it.date().after(from) }
            .filter { it.date().before(to) }
            .filter { it.isNineteen() }
            .filter { !it.isRengo() }
            .filter { it.isNotBotGame() }
            .filter { it.isNotCancelled() }
            .filter { it.isNotCorrespondence() }
            .sortedBy { it.started }
            .toList()

    override fun userGame(user: User, gameServerId: String): UserAccountGame? =
        if (user.ogsId.isNullOrBlank()) throw EmptyUserIdException
        else {
            val url = "${Config.Ogs.API_URL}/games/$gameServerId"
            log(INFO, url)

            val request: Request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                log(INFO, "GET SUCCESS ${response.code}")
                val game = gson.fromJson(response.body!!.string(), OgsGame::class.java)
                response.close()
                game
            } else {
                val error = ApiException("GET FAILURE " + response.code)
                log(ERROR, error.message!!, error)
                throw error
            }
        }

    fun user(id: String?): OgsUser? =
        if (id.isNullOrBlank()) null
        else get("${Config.Ogs.TERMINATION_API_URL}/player/$id", OgsUser::class.java)

    private fun allGamesSince(user: User, limitDate: Date): List<OgsGame> =
        if (user.ogsId.isNullOrBlank()) throw EmptyUserIdException
        else {
            val games: MutableList<OgsGame> = ArrayList()
            var url: String? = "${Config.Ogs.API_URL}/players/${user.ogsId}/games?ordering=-ended"

            while (url.isNullOrBlank().not()) {
                log(INFO, "$url")

                val request: Request = Request.Builder().url(url!!).get().build()
                val response = okHttpClient.newCall(request).execute()

                url = if (response.isSuccessful) {
                    log(INFO, "GET SUCCESS ${response.code}")
                    val gameList = gson.fromJson(response.body!!.string(), OgsGameList::class.java)
                    response.close()
                    games.addAll(gameList.results)
                    if (gameList.results.lastOrNull()?.date()?.before(limitDate) == true) null
                    else gameList.next
                } else {
                    val error = ApiException("GET FAILURE " + response.code)
                    log(ERROR, error.message!!, error)
                    throw error
                }
            }

            log(INFO, "Found ${games.size} games")
            games
        }

    private fun <T : Any> get(route: String, className: Class<T>): T {
        if (lastCallTime.plusSeconds(1).isAfter(ZonedDateTime.now(DATE_ZONE))) {
            // Delay to avoid spamming OGS API
            log(INFO, "Waiting to avoid OGS spam")
            Thread.sleep(Config.Ogs.API_DELAY_IN_SECONDS * 1000L)
        }
        lastCallTime = ZonedDateTime.now(DATE_ZONE)

        val request: Request = Request.Builder().url(route).get().build()
        log(INFO, "GET REQUEST $route")
        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            log(INFO, "GET SUCCESS ${response.code}")
            val apiResponse = gson.fromJson(response.body!!.string(), className)
            response.close()
            apiResponse
        } else {
            val error = ApiException("GET FAILURE " + response.code)
            log(ERROR, error.message!!, error)
            throw error
        }
    }
}
