package com.fulgurogo.features.user.kgs

import com.fulgurogo.TAG
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
import com.fulgurogo.utilities.ApiException
import com.fulgurogo.utilities.EmptyUserIdException
import com.fulgurogo.utilities.filterGame
import com.google.gson.Gson
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.*

class KgsClient : UserAccountClient {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    private val userCache = mutableMapOf<String, CachedKgsUser>()

    override fun user(user: User): KgsUser? = user(user.kgsId)
    override fun userGames(user: User, from: Date, to: Date): List<UserAccountGame> =
        allGames(user)
            .asSequence()
            .filterGame("is too old") { it.date().after(from) }
            .filterGame("is too recent") { it.date().before(to) }
            .filterGame("is not 19x19") { it.isNineteen() }
            .filterGame("has wrong type") { it.isRanked() || it.isFree() || it.isSimu() }
            .sortedBy { it.timestamp }
            .toList()
            .also { log(TAG, "Filtered to ${it.size} games") }
            .filterBotGamesAndTagShortGames(user)

    override fun userGame(user: User, gameServerId: String): UserAccountGame? =
        allGames(user).firstOrNull { it.timestamp == gameServerId }

    private fun allGames(user: User): List<KgsGame> = if (user.kgsId.isNullOrBlank()) throw EmptyUserIdException
    else {
        login()
        val archives = getArchivesFor(user.kgsId)
        val games = archives?.games ?: mutableListOf()
        log(TAG, "Found ${games.size} total games")
        logout()
        games
    }

    private fun List<KgsGame>.filterBotGamesAndTagShortGames(mainPlayer: User): List<KgsGame> {
        if (isEmpty()) return this

        log(TAG, "Inspecting $size games for bots & time settings")
        val nonBotGames = toMutableList()

        forEach { game ->
            log(TAG, "Logging in")
            login()

            log(TAG, "Ensuring bot is in the room")
            if (!joinRoom()) {
                log(TAG, "Can't join room with bot. Exiting")
                return mutableListOf()
            }

            val opponentId =
                if (game.blackPlayerServerId() == mainPlayer.kgsId) game.whitePlayerServerId()
                else game.blackPlayerServerId()
            log(TAG, "Checking user $opponentId")

            // Cache management to avoid multiple requests for the same opponent
            val isBot: Boolean = userCache[opponentId]?.user?.isBot() ?: getDetailsFor(opponentId)?.let {
                // Add user to cache
                userCache[it.user.name] = CachedKgsUser(it.user, it.regStartDate)
                it.user.isBot()
            } ?: false

            if (isBot) {
                log(TAG, "Filtering game ${game.gameId()} because $opponentId is a bot.")
                nonBotGames.remove(game)
            } else {
                log(TAG, "Loading game ${game.gameId()}")
                game.isShortGame = true
                loadGame(game)?.let {
                    log(TAG, "Game ${game.gameId()} loaded")
                    val channelId = it.channelId
                    game.isShortGame = !it.sgfEvents.isLongGame()
                    log(TAG, "Tagging game ${game.timestamp} => isShort: ${game.isShortGame}.")
                    log(TAG, "Exiting game ${game.timestamp}")
                    exitGame(channelId)
                }
            }

            log(TAG, "Logging out")
            logout()
        }

        log(TAG, "Purged to ${nonBotGames.size} non-bot games")
        return nonBotGames
    }

    fun user(id: String?): KgsUser? = try {
        if (id.isNullOrBlank()) throw EmptyUserIdException
        else {
            login()
            val user = getDetailsFor(id)?.user
            logout()
            user
        }
    } catch (e: Exception) {
        null
    }

    private fun login() {
        val success = postGet(gson.toJson(KgsApi.Request.Login())).hasMessageOfType(KgsApi.ChannelType.LOGIN_SUCCESS)
        if (!success) throw ApiException("Login failure: no LOGIN_SUCCESS message")
    }

    private fun logout() = postGet(gson.toJson(KgsApi.Request.Logout()))

    private fun getArchivesFor(id: String): KgsApi.Message? =
        postGet(gson.toJson(KgsApi.Request.ArchiveJoin(id))).getMessageOfType(KgsApi.ChannelType.ARCHIVE_JOIN)

    private fun joinRoom(): Boolean = with(postGet(gson.toJson(KgsApi.Request.Join()))) {
        hasMessageOfType(KgsApi.ChannelType.ROOM_JOIN) || hasMessageOfType(KgsApi.ChannelType.CHANNEL_ALREADY_JOINED)
    }

    private fun getDetailsFor(id: String): KgsApi.Message? =
        postGet(gson.toJson(KgsApi.Request.DetailsJoin(id))).getMessageOfType(KgsApi.ChannelType.DETAILS_JOIN)

    private fun loadGame(game: KgsGame): KgsApi.Message? =
        postGet(gson.toJson(KgsApi.Request.LoadGame(game.timestamp))).getMessageOfType(KgsApi.ChannelType.GAME_JOIN)

    private fun exitGame(channelId: Int) = postGet(gson.toJson(KgsApi.Request.Unjoin(channelId)))

    private fun postGet(jsonPayload: String): KgsApi.Response {
        post(jsonPayload)

        // Delay ensuring response from KGS API which is quite slow to update
        Thread.sleep(Config.get("kgs.api.delay.seconds").toInt() * 1000L)

        return try {
            get()
        } catch (e: Exception) {
            log(TAG, "GET ERROR - Retrying")
            try {
                get()
            } catch (e: Exception) {
                log(TAG, "GET ERROR - Failed 2 times", e)
                throw e
            }
        }
    }

    private fun post(jsonPayload: String) = try {
        log(TAG, "POST $jsonPayload")

        val postRequestBody: RequestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        val postRequest: Request = Request.Builder().url(Config.get("kgs.api.url")).post(postRequestBody).build()
        val postResponse = okHttpClient.newCall(postRequest).execute()

        if (!postResponse.isSuccessful) {
            val error = ApiException("POST FAILURE " + postResponse.code)
            log(TAG, error.message!!, error)
            throw error
        }

        log(TAG, "POST SUCCESS ${postResponse.code}")
        postResponse.close()
    } catch (e: Exception) {
        log(TAG, "POST ERROR", e)
        throw e
    }

    private fun get(): KgsApi.Response {
        log(TAG, "GET")

        val getRequest: Request = Request.Builder().url(Config.get("kgs.api.url")).get().build()
        val getResponse = okHttpClient.newCall(getRequest).execute()

        if (!getResponse.isSuccessful) {
            val error = ApiException("GET FAILURE " + getResponse.code)
            log(TAG, error.message!!, error)
            throw error
        }

        val getResponseBody: String = getResponse.body!!.string()
        log(TAG, "GET SUCCESS ${getResponse.code}")
        val kgsApiResponse = gson.fromJson(getResponseBody, KgsApi.Response::class.java)
        getResponse.close()

        if (kgsApiResponse == null) {
            val error = ApiException("GET ERROR: Response is null")
            log(TAG, error.message!!, error)
            throw error
        }

        return kgsApiResponse
    }
}
