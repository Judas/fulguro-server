package com.fulgurogo.fox.api

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.fox.FoxModule.TAG
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

object FoxApiClient {
    private val gson: Gson = Gson()

    // FOX needs its own authenticator and retry interceptor, but newBuilder() keeps the shared client's connection
    // pool and dispatcher rather than standing up a second set of both.
    private val foxHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .authenticator(FoxAuthenticator())
        .addInterceptor(FoxRetryInterceptor())
        .build()

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
        val response = foxHttpClient.newCall(request).execute()
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
