package com.fulgurogo.api.auth

import com.fulgurogo.api.ApiModule.TAG
import com.fulgurogo.api.db.ApiDatabaseAccessor
import com.fulgurogo.api.db.model.AuthCredentials
import com.fulgurogo.api.db.model.AuthRefreshPayload
import com.fulgurogo.api.db.model.AuthRequestResponse
import com.fulgurogo.api.db.model.ProfileRequestResponse
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.discord.DiscordModule
import com.google.gson.Gson
import okhttp3.Request
import java.time.ZonedDateTime
import java.util.Date

data class DiscordSession(
    val discordId: String,
    val name: String,
    val avatar: String,
    val expirationDate: java.util.Date,
    val roleIds: Set<String>,
)

sealed interface SessionResolution {
    data class Authenticated(val session: DiscordSession) : SessionResolution
    data object Unauthorized : SessionResolution
    data object Unavailable : SessionResolution
}

fun interface SessionResolver {
    fun resolve(goldId: String?): SessionResolution
}

/** Resolves the existing GOLD browser session and its current guild roles. */
class DiscordSessionResolver : SessionResolver {
    private val gson = Gson()

    override fun resolve(goldId: String?): SessionResolution {
        if (goldId.isNullOrBlank()) return SessionResolution.Unauthorized
        val credentials = ApiDatabaseAccessor.getAuthCredentials(goldId) ?: return SessionResolution.Unauthorized
        val validCredentials = if (credentials.expirationDate.before(Date())) {
            refresh(goldId, credentials) ?: return SessionResolution.Unauthorized
        } else {
            credentials
        }

        val discordId = discordId(validCredentials) ?: return SessionResolution.Unauthorized
        val jda = DiscordModule.discordBot.jda ?: return SessionResolution.Unavailable
        val guild = jda.getGuildById(Config.get("bot.guild.id")) ?: return SessionResolution.Unavailable
        val member = guild.getMemberById(discordId) ?: return SessionResolution.Unauthorized

        return SessionResolution.Authenticated(
            DiscordSession(
                discordId = discordId,
                name = member.effectiveName,
                avatar = member.user.effectiveAvatarUrl,
                expirationDate = validCredentials.expirationDate,
                roleIds = member.roles.mapTo(mutableSetOf()) { it.id },
            )
        )
    }

    private fun refresh(goldId: String, credentials: AuthCredentials): AuthCredentials? = try {
        val payload = AuthRefreshPayload(refreshToken = credentials.refreshToken).toFormBody()
        val request = Request.Builder().url(Config.get("gold.discord.auth.token.uri")).post(payload).build()
        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
        ApiDatabaseAccessor.saveAuthCredentials(goldId, gson.fromJson(responseBody, AuthRequestResponse::class.java))
        ApiDatabaseAccessor.getAuthCredentials(goldId)
    } catch (e: Exception) {
        log(TAG, "DISCORD AUTH REFRESH FAILURE ${e.message}")
        null
    }

    private fun discordId(credentials: AuthCredentials): String? = try {
        val request = Request.Builder()
            .url("${Config.get("gold.discord.api.url")}/users/@me")
            .header("Authorization", "${credentials.tokenType} ${credentials.accessToken}")
            .get()
            .build()
        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
        gson.fromJson(responseBody, ProfileRequestResponse::class.java).id
    } catch (e: Exception) {
        log(TAG, "DISCORD PROFILE REQUEST FAILURE ${e.message}")
        null
    }
}
