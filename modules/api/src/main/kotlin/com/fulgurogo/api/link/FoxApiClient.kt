package com.fulgurogo.api.link

import com.fulgurogo.common.utilities.okHttpClientWithoutCookies
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val DEFAULT_FOX_API_URL = "https://fox-go-api.onrender.com"

/** The identity snapshot returned by fox-go-api when a player is linked. */
data class FoxApiPlayer(
    val uid: String? = null,
    val username: String? = null,
    val rank: String? = null,
    val totalwin: Int = 0,
    val totallost: Int = 0,
    val totalequal: Int = 0,
)

/** Small, stateless client for the public FoxWQ lookup service. */
class FoxApiClient(
    baseUrl: String = DEFAULT_FOX_API_URL,
    client: OkHttpClient = okHttpClientWithoutCookies,
    timeoutSeconds: Long = 15,
) {
    private val gson = Gson()
    private val baseUrl = baseUrl.trimEnd('/')
    private val client = client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()

    /** Returns null for an invalid/unknown player and throws when the upstream service itself fails. */
    fun findPlayer(username: String): FoxApiPlayer? {
        val name = username.trim()
        if (name.isEmpty()) return null

        val url = "$baseUrl/api/players/search"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("username", name)
            .build()
        val request = Request.Builder().url(url).get().build()

        return client.newCall(request).execute().use { response ->
            if (response.code == 400 || response.code == 404) return null
            if (!response.isSuccessful) {
                throw IllegalStateException("FOX player search failed with HTTP ${response.code}")
            }

            gson.fromJson(response.body.string(), FoxApiPlayer::class.java)
                ?.takeIf { !it.uid.isNullOrBlank() && !it.username.isNullOrBlank() }
        }
    }
}
