package com.fulgurogo.fox.api

import com.fulgurogo.common.config.Config
import okhttp3.*

class FoxAuthenticator() : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val credentials = Credentials.basic(
            Config.get("fox.bot.username"),
            Config.get("fox.bot.password")
        )
        return response.request.newBuilder()
            .header("Authorization", credentials)
            .build()
    }
}
