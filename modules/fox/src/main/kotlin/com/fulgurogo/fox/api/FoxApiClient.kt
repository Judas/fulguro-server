package com.fulgurogo.fox.api

import com.fulgurogo.common.utilities.okHttpClientWithoutCookies
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val DEFAULT_FOX_API_URL = "https://fox-go-api.onrender.com"

data class FoxApiPlayer(
    val uid: String? = null,
    val username: String? = null,
    val rank: String? = null,
    val totalwin: Int = 0,
    val totallost: Int = 0,
    val totalequal: Int = 0,
)

data class FoxApiGamePage(
    val uid: String? = null,
    val games: List<FoxApiGame> = emptyList(),
    val lastCode: String? = null,
)

data class FoxApiGame(
    val chessid: String? = null,
    val blackuid: String? = null,
    val blacknick: String? = null,
    val blackdan: Int? = null,
    val whiteuid: String? = null,
    val whitenick: String? = null,
    val whitedan: Int? = null,
    val starttime: String? = null,
)

data class FoxApiGameDetail(
    val chessid: String? = null,
    val sgf: String? = null,
)

/** Stateless client for the public fox-go-api facade. */
class FoxApiClient(
    baseUrl: String = DEFAULT_FOX_API_URL,
    client: OkHttpClient = okHttpClientWithoutCookies,
    timeoutSeconds: Long = 20,
) {
    private val gson = Gson()
    private val baseUrl = baseUrl.trimEnd('/')
    private val client = client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()

    /** Returns null for an invalid/unknown player and throws when the upstream service itself fails. */
    fun findPlayer(username: String): FoxApiPlayer? {
        val name = username.trim()
        if (name.isEmpty()) return null

        val url = "$baseUrl/api/players/search".toHttpUrl().newBuilder()
            .addQueryParameter("username", name)
            .build()
        return get(url.toString(), FoxApiPlayer::class.java, nullOnNotFound = true)
            ?.takeIf { !it.uid.isNullOrBlank() && !it.username.isNullOrBlank() }
    }

    fun games(uid: String, lastCode: String? = null): FoxApiGamePage {
        val url = "$baseUrl/api/players/$uid/games".toHttpUrl().newBuilder().apply {
            if (!lastCode.isNullOrBlank()) addQueryParameter("lastCode", lastCode)
        }.build()
        return get(url.toString(), FoxApiGamePage::class.java)
            ?: throw IllegalStateException("FOX games response was empty")
    }

    fun game(chessId: String): FoxApiGameDetail =
        get("$baseUrl/api/games/$chessId", FoxApiGameDetail::class.java)
            ?: throw IllegalStateException("FOX game $chessId response was empty")

    private fun <T> get(url: String, type: Class<T>, nullOnNotFound: Boolean = false): T? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (nullOnNotFound && (response.code == 400 || response.code == 404)) return null
            if (!response.isSuccessful) {
                throw IllegalStateException("fox-go-api request failed with HTTP ${response.code}")
            }
            gson.fromJson(response.body.string(), type)
        }
    }
}
