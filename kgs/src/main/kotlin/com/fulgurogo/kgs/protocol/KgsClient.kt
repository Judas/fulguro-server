package com.fulgurogo.kgs.protocol

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.Logger.Level.*
import com.fulgurogo.common.logger.log
import com.fulgurogo.kgs.db.model.KgsUserInfo
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

class KgsClient {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .build()

    fun getUserInfo(stale: KgsUserInfo): KgsUserInfo =
        stale.kgsId?.let { kgsId ->
            login()
            val kgsUser = getDetailsFor(kgsId)?.user
            logout()

            kgsUser?.let { user ->
                KgsUserInfo(
                    discordId = stale.discordId,
                    kgsId = kgsId,
                    kgsRank = user.rank.ifBlank { "?" },
                    stable = !user.rank.contains("?"),
                    updated = Date()
                )
            } ?: stale
        } ?: stale


    private fun login() {
        val success = postGet(gson.toJson(KgsApi.Request.Login())).hasMessageOfType(KgsApi.ChannelType.LOGIN_SUCCESS)
        if (!success) throw Exception("Login failure: no LOGIN_SUCCESS message")
    }

    private fun logout() = postGet(gson.toJson(KgsApi.Request.Logout()))

    private fun getDetailsFor(id: String): KgsApi.Message? =
        postGet(gson.toJson(KgsApi.Request.DetailsJoin(id))).getMessageOfType(KgsApi.ChannelType.DETAILS_JOIN)

    private fun postGet(jsonPayload: String): KgsApi.Response {
        post(jsonPayload)

        // Delay ensuring response from KGS API which is quite slow to update
        Thread.sleep(Config.get("kgs.api.delay.seconds").toInt() * 1000L)

        return try {
            get()
        } catch (e: Exception) {
            log(WARNING, "GET ERROR - Retrying")
            try {
                get()
            } catch (e: Exception) {
                log(ERROR, "GET ERROR - Failed 2 times", e)
                throw e
            }
        }
    }

    private fun post(jsonPayload: String) = try {
        log(INFO, "POST $jsonPayload")

        val postRequestBody: RequestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        val postRequest: Request = Request.Builder().url(Config.get("kgs.api.url")).post(postRequestBody).build()
        val postResponse = okHttpClient.newCall(postRequest).execute()

        if (!postResponse.isSuccessful) {
            val error = Exception("POST FAILURE " + postResponse.code)
            log(ERROR, error.message!!, error)
            throw error
        }

        log(INFO, "POST SUCCESS ${postResponse.code}")
        postResponse.close()
    } catch (e: Exception) {
        log(ERROR, "POST ERROR", e)
        throw e
    }

    private fun get(): KgsApi.Response {
        log(INFO, "GET")

        val getRequest: Request = Request.Builder().url(Config.get("kgs.api.url")).get().build()
        val getResponse = okHttpClient.newCall(getRequest).execute()

        if (!getResponse.isSuccessful) {
            val error = Exception("GET FAILURE " + getResponse.code)
            log(ERROR, error.message!!, error)
            throw error
        }

        val getResponseBody: String = getResponse.body!!.string()
        log(INFO, "GET SUCCESS ${getResponse.code}")
        val kgsApiResponse = gson.fromJson(getResponseBody, KgsApi.Response::class.java)
        getResponse.close()

        if (kgsApiResponse == null) {
            val error = Exception("GET ERROR: Response is null")
            log(ERROR, error.message!!, error)
            throw error
        }

        return kgsApiResponse
    }
}