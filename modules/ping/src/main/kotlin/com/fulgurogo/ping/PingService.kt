package com.fulgurogo.ping

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.ping.PingModule.TAG
import okhttp3.Request

class PingService : PeriodicFlowService(0, 600) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Ping frontend
        try {
            val route = Config.get("frontend.url")
            val request = Request.Builder()
                .url(route)
                .header("User-Agent", Config.get("user.agent"))
                .get().build()
            val response = okHttpClient().newCall(request).execute()

            if (response.isSuccessful) log(TAG, "Pinged frontend at $route")
            else log(TAG, "Frontend responding code ${response.code}")

            response.close()
        } catch (e: Exception) {
            log(TAG, "Failed to reach frontend ${e.message}")
        }

        processing = false
    }
}
