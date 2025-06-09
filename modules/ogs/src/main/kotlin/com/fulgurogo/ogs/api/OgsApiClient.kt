package com.fulgurogo.ogs.api

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.ogs.OgsModule.TAG
import com.google.gson.Gson
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object OgsApiClient {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()
    private var lastNetworkCallTime: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

    private fun ensureSpamDelay() {
        // Delay to avoid spamming OGS API: ensure between 500ms & 1500ms free time
        val now = ZonedDateTime.now(DATE_ZONE)
        if (lastNetworkCallTime.plusSeconds(1).isAfter(now))
            Thread.sleep(500)
        lastNetworkCallTime = ZonedDateTime.now(DATE_ZONE)
    }

    fun <T : Any> get(route: String, className: Class<T>): T =
        gson.fromJson(get(route), className)

    fun <T : Any> post(route: String, body: Any, className: Class<T>): T =
        gson.fromJson(post(route, gson.toJson(body)), className)

    fun get(route: String): String {
        ensureSpamDelay()

        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .get().build()
        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            val responseBody = response.body!!.string()
            response.close()
            responseBody
        } else {
            val error = Exception("GET FAILURE " + response.code)
            log(TAG, error.message!!, error)
            response.close()
            throw error
        }
    }

    fun post(route: String, body: String): String {
        ensureSpamDelay()

        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .post(requestBody).build()
        val response = okHttpClient.newCall(request).execute()

        return if (response.isSuccessful) {
            val responseBody = response.body!!.string()
            response.close()
            responseBody
        } else {
            val error = Exception("POST FAILURE " + response.code)
            log(TAG, error.message!!, error)
            response.close()
            throw error
        }
    }
}
