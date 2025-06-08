package com.fulgurogo.ping

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.ping.PingModule.TAG
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

class PingService : PeriodicFlowService(0, 600) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Ping frontend
        val route = Config.get("frontend.url")
        val request = Request.Builder()
            .url(route)
            .header("User-Agent", Config.get("user.agent"))
            .get().build()
        val response = okHttpClient.newCall(request).execute()

        if (response.isSuccessful) log(TAG, "Pinged frontend at $route")
        else log(TAG, "Frontend responding code ${response.code}")

        response.close()

        processing = false
    }
}
