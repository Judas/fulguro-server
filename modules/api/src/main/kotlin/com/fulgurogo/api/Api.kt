package com.fulgurogo.api

import com.fulgurogo.api.ApiModule.TAG
import com.fulgurogo.api.db.ApiDatabaseAccessor
import com.fulgurogo.api.db.model.*
import com.fulgurogo.api.utilities.internalError
import com.fulgurogo.api.utilities.notFoundError
import com.fulgurogo.api.utilities.rateLimit
import com.fulgurogo.api.utilities.standardResponse
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.egf.db.EgfDatabaseAccessor
import com.fulgurogo.ffg.db.FfgDatabaseAccessor
import com.fulgurogo.fgc.db.FgcDatabaseAccessor
import com.fulgurogo.fox.db.FoxDatabaseAccessor
import com.fulgurogo.gold.db.GoldDatabaseAccessor
import com.fulgurogo.igs.db.IgsDatabaseAccessor
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.ogs.api.OgsApiClient
import com.fulgurogo.ogs.api.model.OgsUserList
import com.fulgurogo.ogs.db.OgsDatabaseAccessor
import com.google.gson.Gson
import io.javalin.http.Context
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class Api {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    fun getPlayers(context: Context) = try {
        context.rateLimit()
        val players = ApiDatabaseAccessor.apiPlayers()
        context.standardResponse(players)
    } catch (e: Exception) {
        log(TAG, "getPlayers ${e.message}")
        context.internalError()
    }

    fun getPlayerProfile(context: Context) = try {
        context.rateLimit()

        val playerId = context.pathParam("id")
        val player = ApiDatabaseAccessor.apiPlayer(playerId)
        player?.let { p ->
            p.games = ApiDatabaseAccessor.apiGamesFor(playerId)
            context.standardResponse(p)
        } ?: context.notFoundError()
    } catch (e: Exception) {
        log(TAG, "getPlayerProfile", e)
        context.internalError()
    }

    fun getRecentGames(context: Context) = try {
        context.rateLimit()
        val games = ApiDatabaseAccessor.recentGames()
        context.standardResponse(games)
    } catch (e: Exception) {
        log(TAG, "getPlayerProfile", e)
        context.internalError()
    }

    fun getGame(context: Context) = try {
        context.rateLimit()
        val goldId = context.pathParam("id")

        val game = ApiDatabaseAccessor.apiGame(goldId)
        game?.let { context.standardResponse(it) } ?: context.notFoundError()
    } catch (e: Exception) {
        log(TAG, "getGame", e)
        context.internalError()
    }

    fun getTiers(context: Context) = try {
        context.rateLimit()
        val tiers = GoldDatabaseAccessor.tiers()
        context.standardResponse(tiers)
    } catch (e: Exception) {
        log(TAG, "getTiers", e)
        context.internalError()
    }

    fun authenticateUser(context: Context) = try {
        context.rateLimit()

        val body = gson.fromJson(context.body(), AuthRequestBody::class.java)
        val authRequestResponse = requestAuthToken(body.code)
        ApiDatabaseAccessor.saveAuthCredentials(body.goldId, authRequestResponse)
        context.standardResponse()
    } catch (e: Exception) {
        log(TAG, "authenticateUser", e)
        context.internalError()
    }

    fun getAuthProfile(context: Context) = try {
        context.rateLimit()

        val goldIdParam = context.queryParam("goldId")
        goldIdParam?.let { goldId ->
            // Get corresponding token
            val credentials = ApiDatabaseAccessor.getAuthCredentials(goldId)
            credentials?.let { creds ->
                var validCredentials: AuthCredentials? = creds

                // Check expiration
                if (creds.expirationDate.before(ZonedDateTime.now(DATE_ZONE).toDate())) {
                    val authRequestResponse = refreshAuthToken(refreshToken = creds.refreshToken)
                    ApiDatabaseAccessor.saveAuthCredentials(goldId, authRequestResponse)
                    validCredentials = ApiDatabaseAccessor.getAuthCredentials(goldId)
                }

                validCredentials?.let { validCreds ->
                    DiscordModule.discordBot.jda?.let { jda ->
                        // Fetch user discord info
                        val discordId = getUserDiscordId(validCreds)
                        val discordUser = jda.getUserById(discordId)
                        val guild = jda.getGuildById(Config.get("bot.guild.id"))
                        val discordName = discordUser?.let {
                            guild?.getMember(it)?.effectiveName ?: it.name
                        } ?: discordId
                        if (discordName == discordId) {
                            context.notFoundError()  // User is not on the server
                        } else {
                            val discordAvatar = DiscordModule.discordBot.jda?.getUserById(discordId)?.effectiveAvatarUrl
                                ?: Config.get("gold.default.avatar")

                            // Create user in DB if needed
                            DiscordDatabaseAccessor.createUser(discordId, discordName, discordAvatar)

                            // Return API Profile
                            val profile = ApiProfile(discordId, discordName, discordAvatar, validCreds.expirationDate)
                            context.standardResponse(profile)
                        }
                    } ?: throw IllegalStateException("JDA is null")
                } ?: context.notFoundError()
            } ?: context.notFoundError()
        } ?: context.notFoundError()
    } catch (e: Exception) {
        log(TAG, "getAuthProfile", e)
        context.internalError()
    }

    private fun requestAuthToken(authCode: String): AuthRequestResponse {
        val body: RequestBody = AuthRequestPayload(code = authCode).toFormBody()

        val request: Request = Request.Builder().url(Config.get("gold.discord.auth.token.uri")).post(body).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = Exception("DISCORD AUTH REQUEST FAILURE " + response.code)
            log(TAG, error.message!!, error)
            response.close()
            throw error
        }

        log(TAG, "DISCORD AUTH REQUEST SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun refreshAuthToken(refreshToken: String): AuthRequestResponse {
        val body: RequestBody = AuthRefreshPayload(refreshToken = refreshToken).toFormBody()
        val request: Request = Request.Builder().url(Config.get("gold.discord.auth.token.uri")).post(body).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = Exception("DISCORD AUTH REFRESH FAILURE " + response.code)
            log(TAG, error.message!!, error)
            response.close()
            throw error
        }

        log(TAG, "DISCORD AUTH REFRESH SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun getUserDiscordId(authCredentials: AuthCredentials): String {
        val url = "${Config.get("gold.discord.api.url")}/users/@me"
        val request: Request = Request.Builder()
            .url(url)
            .header("Authorization", "${authCredentials.tokenType} ${authCredentials.accessToken}")
            .get().build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = Exception("DISCORD PROFILE REQUEST FAILURE " + response.code)
            log(TAG, error.message!!, error)
            response.close()
            throw error
        }

        log(TAG, "DISCORD PROFILE REQUEST SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, ProfileRequestResponse::class.java).id
    }

    fun getAccounts(context: Context) = try {
        context.rateLimit()
        context.standardResponse(listOf("KGS", "OGS", "FOX", "IGS", "FFG", "EGF"))
    } catch (e: Exception) {
        log(TAG, "getAccounts", e)
        context.internalError()
    }

    fun link(context: Context) = try {
        context.rateLimit()

        // Param validation
        val body = gson.fromJson(context.body(), LinkRequestBody::class.java)

        if (body.accountId.isBlank() || body.discordId.isBlank()) {
            context.notFoundError()
        } else {
            // Check that discord id exists
            val discordUser = DiscordDatabaseAccessor.user(body.discordId)
            when {
                discordUser == null -> context.notFoundError()
                body.account == "OGS" -> {
                    val url = "${Config.get("ogs.api.url")}/players?username=${body.accountId}"
                    val userList = OgsApiClient.get(url, OgsUserList::class.java)
                    userList.results.firstOrNull()?.id?.toString()?.let {
                        linkAccount(context, LinkRequestBody(body.discordId, "OGS", it))
                    }
                }

                else -> linkAccount(context, body)
            }
        }
    } catch (e: Exception) {
        log(TAG, "link", e)
        context.internalError()
    }

    private fun linkAccount(context: Context, body: LinkRequestBody) {
        // Check if this account is free to link
        when (body.account) {
            "KGS" -> {
                if (KgsDatabaseAccessor.user(body.accountId) != null) context.internalError()
                else KgsDatabaseAccessor.addUser(body.discordId, body.accountId)
            }

            "OGS" -> {
                if (OgsDatabaseAccessor.user(body.accountId.toInt()) != null) context.internalError()
                else OgsDatabaseAccessor.addUser(body.discordId, body.accountId)
            }

            "FOX" -> {
                if (FoxDatabaseAccessor.user(body.accountId.toInt()) != null) context.internalError()
                else FoxDatabaseAccessor.addUser(body.discordId, body.accountId)
            }

            "IGS" -> {
                if (IgsDatabaseAccessor.user(body.accountId.toInt()) != null) context.internalError()
                else IgsDatabaseAccessor.addUser(body.discordId, body.accountId)
            }

            "FFG" -> {
                if (FfgDatabaseAccessor.user(body.accountId.toInt()) != null) context.internalError()
                else FfgDatabaseAccessor.addUser(body.discordId, body.accountId)
            }

            "EGF" -> {
                if (EgfDatabaseAccessor.user(body.accountId.toInt()) != null) context.internalError()
                else EgfDatabaseAccessor.addUser(body.discordId, body.accountId)
            }

            else -> context.internalError()
        }

        // Add in others DB
        GoldDatabaseAccessor.addPlayer(body.discordId)
        FgcDatabaseAccessor.addPlayer(body.discordId)
        context.standardResponse()
    }
}
