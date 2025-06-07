package com.fulgurogo.fox.api

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

class FoxRetryInterceptor : Interceptor {
    companion object {
        private const val MAX_RETRIES = 10
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var tryCount = 0
        var response: Response = chain.proceed(chain.request())
        while (!response.isSuccessful && tryCount < MAX_RETRIES) {
            Thread.sleep(500)
            tryCount++
            response.close()
            response = chain.call().clone().execute()
        }
        return response
    }
}
