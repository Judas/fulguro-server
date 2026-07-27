package com.fulgurogo.fox.api

import com.fulgurogo.common.config.Config
import okhttp3.*

/** Answers a FOX 401 with basic credentials. */
class FoxAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // We already sent credentials and still got a 401, so sending the same ones again will not help. Returning null
        // gives up. Without this, okhttp keeps re-authenticating up to its own follow-up ceiling, so misconfigured
        // credentials produce a burst of identical requests on every call instead of one clean failure.
        if (response.request.header("Authorization") != null) return null

        val credentials = Credentials.basic(
            Config.get("fox.bot.username"),
            Config.get("fox.bot.password")
        )
        return response.request.newBuilder()
            .header("Authorization", credentials)
            .build()
    }
}
