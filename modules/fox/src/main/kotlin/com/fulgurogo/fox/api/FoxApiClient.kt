package com.fulgurogo.fox.api

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.fox.FoxModule.TAG
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

object FoxApiClient {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .authenticator(FoxAuthenticator())
        .addInterceptor(FoxRetryInterceptor())
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    fun <T : Any> get(route: String, className: Class<T>): T =
        gson.fromJson(get(route), className)

    fun <T : Any> get(route: String, typeToken: TypeToken<List<T>>): List<T> =
        gson.fromJson(get(route), typeToken.type)

    fun get(route: String): String {
        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .header("X-APP-ID", Config.get("fox.app.id"))
            .header("X-API-KEY", Config.get("fox.api.key"))
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
}
