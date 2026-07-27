package com.fulgurogo.fox.api

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

/**
 * Retries an unsuccessful FOX response a few times, spacing the attempts out.
 *
 * Retries go through [Interceptor.Chain.proceed], **not** `chain.call().clone().execute()`. The latter starts a fresh
 * call through the whole interceptor chain, so it re-enters this interceptor: each attempt opened its own retry loop
 * and only returned once it had succeeded. MAX_RETRIES bounded nothing, a sustained FOX outage became an unbounded
 * retry at two requests a second against a rate-sensitive API, and the stack grew one frame set per attempt.
 */
class FoxRetryInterceptor : Interceptor {
    companion object {
        private const val MAX_RETRIES = 10
        private const val RETRY_DELAY_MS = 500L
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var response: Response = chain.proceed(chain.request())
        var tryCount = 0

        while (!response.isSuccessful && tryCount < MAX_RETRIES) {
            response.close()
            tryCount++
            Thread.sleep(RETRY_DELAY_MS)
            response = chain.proceed(chain.request())
        }

        return response
    }
}
