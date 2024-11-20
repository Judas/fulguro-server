package com.fulgurogo.features.user.ogs

import com.fulgurogo.Config
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.features.user.UserAccountLiveGame
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
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
            .filterGame("is too old") { it.date().after(from) }
            .filterGame("is too recent") { it.date().before(to) }
            .filterGame("is not 19x19") { it.isNineteen() }
            .filterGame("is rengo") { !it.isRengo() }
            .filterGame("is a bot game") { it.isNotBotGame() }
            .filterGame("is cancelled") { it.isNotCancelled() }
            .filterGame("is correspondence") { it.isNotCorrespondence() }
            .sortedBy { it.started }
            .toList()
            .also { log(INFO, "Filtered to ${it.size} games") }

    override fun userGame(user: User, gameServerId: String): UserAccountGame =
        if (user.ogsId.isNullOrBlank()) throw EmptyUserIdException
        else {
            val url = "${Config.Ogs.API_URL}/games/$gameServerId"
            log(INFO, url)
            get(url, OgsGame::class.java)
        }

    override fun liveGames(): List<UserAccountLiveGame> = TODO()

    fun user(id: String?): OgsUser? =
        if (id.isNullOrBlank()) null
        else get("${Config.Ogs.TERMINATION_API_URL}/player/$id", OgsUser::class.java)

    fun userIdFromPseudo(pseudo: String): String? {
        val url = "${Config.Ogs.API_URL}/players?username=$pseudo"
        val userList = get(url, OgsUserList::class.java)
        return userList.results.firstOrNull()?.id()
    }

    private fun allGamesSince(user: User, limitDate: Date): List<OgsGame> =
        if (user.ogsId.isNullOrBlank()) throw EmptyUserIdException
        else {
            val games: MutableList<OgsGame> = ArrayList()
            var url: String? = "${Config.Ogs.API_URL}/players/${user.ogsId}/games?ordering=-ended"

            while (url.isNullOrBlank().not()) {
                log(INFO, "$url")

                val gameList = get(url!!, OgsGameList::class.java)
                games.addAll(gameList.results)

                val lastGameEndDate = gameList.results.lastOrNull()?.endDate()
                url = when {
                    lastGameEndDate == null -> gameList.next // Last game in list is ongoing game
                    lastGameEndDate.after(limitDate) -> gameList.next // Last game in list is in time interval
                    else -> null // Last game in list is outside time interval
                }
            }

            log(INFO, "Found ${games.size} total games")
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
