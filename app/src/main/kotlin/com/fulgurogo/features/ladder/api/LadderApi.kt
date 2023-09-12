package com.fulgurogo.features.ladder.api

import com.fulgurogo.Config
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
import com.google.gson.Gson
import io.javalin.http.Context
import net.dv8tion.jda.api.JDA
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime

class LadderApi(private val jda: JDA) {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    fun getPlayers(context: Context) = try {
        context.rateLimit()
        val players = DatabaseAccessor.apiLadderPlayers()
        context.standardResponse(players)
    } catch (e: Exception) {
        log(ERROR, "getPlayers", e)
        context.internalError()
    }

    fun getPlayerProfile(context: Context) = try {
        context.rateLimit()

        val playerId = context.pathParam("id")
        val player = DatabaseAccessor.apiLadderPlayer(playerId)

        player?.let { p ->
            // Stability
            p.stability = DatabaseAccessor.stability(p.discordId)

            // Games
            p.games = DatabaseAccessor.apiLadderGamesFor(p.discordId)
                .map { ApiGame.from(it, p.discordId == it.blackPlayerDiscordId) }
                .toMutableList()

            // Accounts
            p.accounts = DatabaseAccessor.ensureUser(p.discordId).toApiAccounts()

            context.standardResponse(p)
        } ?: context.notFoundError()
    } catch (e: Exception) {
        log(ERROR, "getPlayerProfile", e)
        context.internalError()
    }

    fun getRecentGames(context: Context) = try {
        context.rateLimit()
        val latestGames = DatabaseAccessor
            .apiLadderRecentGames()
            .map { ApiGame.from(it) }

        context.standardResponse(latestGames)
    } catch (e: Exception) {
        log(ERROR, "getRecentGames", e)
        context.internalError()
    }

    fun getGame(context: Context) = try {
        context.rateLimit()
        val gameId = context.pathParam("id")

        val game = DatabaseAccessor
            .apiLadderGame(gameId)
            ?.let { ApiGame.from(it) }

        game?.let { context.standardResponse(it) } ?: context.notFoundError()
    } catch (e: Exception) {
        log(ERROR, "getGame", e)
        context.internalError()
    }

    fun getStabilityOptions(context: Context) = try {
        context.rateLimit()
        DatabaseAccessor.stability()
            ?.let { context.standardResponse(it) }
            ?: context.notFoundError()
    } catch (e: Exception) {
        log(ERROR, "getStabilityOptions", e)
        context.internalError()
    }

    fun getTiers(context: Context) = try {
        context.rateLimit()
        DatabaseAccessor.tiers().let { context.standardResponse(it) }
    } catch (e: Exception) {
        log(ERROR, "getTiers", e)
        context.internalError()
    }

    fun authenticateUser(context: Context) = try {
        context.rateLimit()

        val authCode = context.queryParam("code")
        val goldId = context.queryParam("goldId")
        if (goldId == null || authCode == null) {
            log(ERROR, "DISCORD AUTH MISSING PARAMS")
            context.notFoundError()
        } else {
            val authRequestResponse = requestAuthToken(authCode)
            DatabaseAccessor.saveAuthCredentials(goldId, authRequestResponse)
            context.standardResponse()
        }
    } catch (e: Exception) {
        log(ERROR, "authenticateUser", e)
        context.internalError()
    }

    fun getAuthProfile(context: Context) = try {
        context.rateLimit()

        val goldIdParam = context.queryParam("goldId")
        goldIdParam?.let { goldId ->
            // Get corresponding token
            val credentials = DatabaseAccessor.getAuthCredentials(goldId)
            credentials?.let { creds ->
                var validCredentials: AuthCredentials? = creds

                // Check expiration
                if (creds.expirationDate.before(ZonedDateTime.now(DATE_ZONE).toDate())) {
                    log(INFO, "Refreshing expired token")
                    val authRequestResponse = refreshAuthToken(refreshToken = creds.refreshToken)
                    DatabaseAccessor.saveAuthCredentials(goldId, authRequestResponse)
                    validCredentials = DatabaseAccessor.getAuthCredentials(goldId)
                }

                validCredentials?.let { validCreds ->
                    // Fetch user discord id
                    val discordId = getUserDiscordId(validCreds)

                    // Create user if needed
                    val rawUser = DatabaseAccessor.ensureUser(discordId)
                    val user = rawUser.cloneUserWithUpdatedProfile(jda, false)
                    if (user.name == user.discordId) {
                        // User is not on the server
                        context.notFoundError()
                    } else {
                        DatabaseAccessor.updateSimpleUser(user)
                        val profile = ApiProfile(user.discordId, user.name, user.avatar, validCreds.expirationDate)
                        context.standardResponse(profile)
                    }
                } ?: context.notFoundError()
            } ?: context.notFoundError()
        } ?: context.notFoundError()
    } catch (e: Exception) {
        log(ERROR, "getAuthProfile", e)
        context.internalError()
    }

    private fun requestAuthToken(authCode: String): AuthRequestResponse {
        val body: RequestBody = AuthRequestPayload(code = authCode).toFormBody()
        val request: Request = Request.Builder().url(Config.Ladder.DISCORD_AUTH_TOKEN_URL).post(body).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = ApiException("DISCORD AUTH REQUEST FAILURE " + response.code)
            log(ERROR, error.message!!, error)
            throw error
        }

        log(INFO, "DISCORD AUTH REQUEST SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun refreshAuthToken(refreshToken: String): AuthRequestResponse {
        val body: RequestBody = AuthRefreshPayload(refreshToken = refreshToken).toFormBody()
        val request: Request = Request.Builder().url(Config.Ladder.DISCORD_AUTH_TOKEN_URL).post(body).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = ApiException("DISCORD AUTH REFRESH FAILURE " + response.code)
            log(ERROR, error.message!!, error)
            throw error
        }

        log(INFO, "DISCORD AUTH REFRESH SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun getUserDiscordId(authCredentials: AuthCredentials): String {
        val url = "${Config.Ladder.DISCORD_API_URL}/users/@me"
        val request: Request = Request.Builder()
            .url(url)
            .header("Authorization", "${authCredentials.tokenType} ${authCredentials.accessToken}")
            .get().build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = ApiException("DISCORD PROFILE REQUEST FAILURE " + response.code)
            log(ERROR, error.message!!, error)
            throw error
        }

        log(INFO, "DISCORD PROFILE REQUEST SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, ProfileRequestResponse::class.java).id
    }
}
