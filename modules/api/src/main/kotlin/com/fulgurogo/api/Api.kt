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
import com.fulgurogo.common.utilities.okHttpClient
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
import io.javalin.http.HttpResponseException
import okhttp3.Request
import okhttp3.RequestBody
import java.time.ZonedDateTime

class Api {
    private val ogsApiClient = OgsApiClient()
    private val gson: Gson = Gson()

    /**
     * Rate limits, then runs [handler], turning anything unexpected into a 500.
     *
     * The rate limit check deliberately sits *outside* the catch. NaiveRateLimit signals by throwing
     * TooManyRequestsResponse, so while it was inside, every rate-limited request was reported as a 500 and clients had
     * no way to tell they should back off. Javalin maps HttpResponseException to its own status, hence the rethrow.
     */
    private inline fun Context.handle(route: String, handler: () -> Unit) {
        rateLimit()
        try {
            handler()
        } catch (e: HttpResponseException) {
            throw e
        } catch (e: Exception) {
            log(TAG, "$route FAILURE ${e.message}", e)
            internalError()
        }
    }

    fun getPlayers(context: Context) = context.handle("getPlayers") {
        val players = ApiDatabaseAccessor.apiPlayers()
        context.standardResponse(players)
    }

    fun getPlayerProfile(context: Context) = context.handle("getPlayerProfile") {
        val playerId = context.pathParam("id")
        val player = ApiDatabaseAccessor.apiPlayer(playerId)
        player?.let { p ->
            p.games = ApiDatabaseAccessor.apiGamesFor(playerId)
            context.standardResponse(p)
        } ?: context.notFoundError()
    }

    fun getRecentGames(context: Context) = context.handle("getRecentGames") {
        val games = ApiDatabaseAccessor.recentGames()
        context.standardResponse(games)
    }

    fun getGame(context: Context) = context.handle("getGame") {
        val goldId = context.pathParam("id")

        val game = ApiDatabaseAccessor.apiGame(goldId)
        game?.let { context.standardResponse(it) } ?: context.notFoundError()
    }

    fun getTiers(context: Context) = context.handle("getTiers") {
        val tiers = GoldDatabaseAccessor.tiers()
        context.standardResponse(tiers)
    }

    fun authenticateUser(context: Context) = context.handle("authenticateUser") {
        val body = gson.fromJson(context.body(), AuthRequestBody::class.java)
        val authRequestResponse = requestAuthToken(body.code)
        ApiDatabaseAccessor.saveAuthCredentials(body.goldId, authRequestResponse)
        context.standardResponse()
    }

    fun getAuthProfile(context: Context) = context.handle("getAuthProfile") {
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
    }

    private fun requestAuthToken(authCode: String): AuthRequestResponse {
        val body: RequestBody = AuthRequestPayload(code = authCode).toFormBody()

        val request: Request = Request.Builder().url(Config.get("gold.discord.auth.token.uri")).post(body).build()
        val response = okHttpClient().newCall(request).execute()

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
        val response = okHttpClient().newCall(request).execute()

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
        val response = okHttpClient().newCall(request).execute()

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

    fun getAccounts(context: Context) = context.handle("getAccounts") {
        context.standardResponse(listOf("KGS", "OGS", "FOX", "IGS", "FFG", "EGF"))
    }

    fun link(context: Context) = context.handle("link") {
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
                    val userList = ogsApiClient.get(url, OgsUserList::class.java)
                    userList.results.firstOrNull()?.id?.toString()?.let {
                        linkAccount(context, LinkRequestBody(body.discordId, "OGS", it))
                    }
                }

                else -> linkAccount(context, body)
            }
        }
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
