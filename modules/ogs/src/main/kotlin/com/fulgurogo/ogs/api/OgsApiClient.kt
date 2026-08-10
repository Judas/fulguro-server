package com.fulgurogo.ogs.api

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.ogs.OgsModule.TAG
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.time.ZonedDateTime

class OgsApiClient {
    private val gson: Gson = Gson()
    private var lastNetworkCallTime: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

    /**
     * Delay to avoid spamming OGS API: ensure between 500ms & 1500ms free time.
     *
     * Blocking rather than `delay`, unlike the equivalent in KgsService: this client is also called from the Javalin
     * request thread in `Api.link`, which is not a coroutine. Blocking is correct on Dispatchers.IO anyway; making it
     * suspend would only push runBlocking into the API layer.
     */
    private fun ensureSpamDelay() {
        val now = ZonedDateTime.now(DATE_ZONE)
        if (lastNetworkCallTime.plusSeconds(1).isAfter(now))
            Thread.sleep(500)
        lastNetworkCallTime = ZonedDateTime.now(DATE_ZONE)
    }

    fun <T : Any> get(route: String, className: Class<T>): T =
        gson.fromJson(get(route), className)

    fun <T : Any> post(route: String, body: Any, className: Class<T>): T =
        gson.fromJson(post(route, gson.toJson(body)), className)

    /**
     * [headers] is additive and empty by default, which is what keeps `OgsService` and `OgsRealTimeService` behaving
     * exactly as before. It exists for the league, whose endpoints need the two `X-OGS-LEAGUE*` headers on every request.
     *
     * `User-Agent` is set first so a caller passing one of its own overrides it rather than duplicating it.
     */
    fun get(route: String, headers: Map<String, String> = mapOf()): String {
        ensureSpamDelay()

        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get().build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = Exception("GET FAILURE ${response.code} on $route ${response.explain()}")
                log(TAG, error.message!!)
                throw error
            }
            response.body.string()
        }
    }

    fun post(route: String, body: String, headers: Map<String, String> = mapOf()): String {
        ensureSpamDelay()

        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(requestBody).build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = Exception("POST FAILURE ${response.code} on $route ${response.explain()}")
                log(TAG, error.message!!)
                throw error
            }
            response.body.string()
        }
    }

    /** Only the league uses this: `PUT /member/{id}` is how a member is registered, and it has no other caller. */
    fun put(route: String, body: String, headers: Map<String, String> = mapOf()): String {
        ensureSpamDelay()

        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .put(requestBody).build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = Exception("PUT FAILURE ${response.code} on $route ${response.explain()}")
                log(TAG, error.message!!)
                throw error
            }
            response.body.string()
        }
    }
}

/**
 * The first 500 characters of an error body, for the message of a failed call.
 *
 * Without this a rejection is a bare status code, and OGS says exactly what is wrong in the body — a `byoyomi` with no
 * `periods` answers `{"error": "Missing parameters for byoyomi time control (periods, period_time, main_time)"}`. Reading
 * a 400 used to mean reproducing the call by hand with curl.
 *
 * Only ever called on a failure, so no successful response is read twice, and truncated because an OGS error page can be
 * a full HTML document.
 */
private fun Response.explain(): String = try {
    body.string().take(500).replace(Regex("\\s+"), " ").trim()
} catch (e: Exception) {
    "<body unreadable: ${e.message}>"
}
