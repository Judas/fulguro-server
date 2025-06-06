package com.fulgurogo.features.api

import com.fulgurogo.TAG
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.features.bot.FulguroBot
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.exam.ExamSpecialization
import com.fulgurogo.features.games.GameScanner
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.ogs.OgsClient
import com.fulgurogo.utilities.*
import com.google.gson.Gson
import io.javalin.http.Context
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime

object Api {
    private val gson: Gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    fun getPlayers(context: Context) = try {
        context.rateLimit()
        val players = DatabaseAccessor.apiLadderPlayers()
        context.standardResponse(players)
    } catch (e: Exception) {
        log(TAG, "getPlayers", e)
        context.internalError()
    }

    fun getPlayerProfile(context: Context) = try {
        context.rateLimit()

        val playerId = context.pathParam("id")
        val player = DatabaseAccessor.apiLadderPlayer(playerId) ?: ApiPlayer.default(playerId)

        player?.let { p ->
            // Stability
            p.fgcValidation = DatabaseAccessor.fgcValidation(p.discordId)

            // Games
            p.games = DatabaseAccessor.apiLadderGamesFor(p.discordId)
                .map { ApiGame.from(it, p.discordId == it.blackPlayerDiscordId) }
                .toMutableList()

            // Accounts
            p.accounts = DatabaseAccessor.ensureUser(p.discordId).toApiAccounts()

            // Exam
            p.exam = ApiExamPlayer.from(DatabaseAccessor.examPlayer(p.discordId), p)

            context.standardResponse(p)
        } ?: context.notFoundError()
    } catch (e: Exception) {
        log(TAG, "getPlayerProfile", e)
        context.internalError()
    }

    fun getRecentGames(context: Context) = try {
        context.rateLimit()
        val latestGames = DatabaseAccessor
            .apiLadderRecentGames()
            .map { ApiGame.from(it) }

        context.standardResponse(latestGames)
    } catch (e: Exception) {
        log(TAG, "getRecentGames", e)
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
        log(TAG, "getGame", e)
        context.internalError()
    }

    fun getFgcValidation(context: Context) = try {
        context.rateLimit()
        DatabaseAccessor.fgcValidation()
            ?.let { context.standardResponse(it) }
            ?: context.notFoundError()
    } catch (e: Exception) {
        log(TAG, "getFgcValidation", e)
        context.internalError()
    }

    fun getTiers(context: Context) = try {
        context.rateLimit()
        DatabaseAccessor.tiers().let { context.standardResponse(it) }
    } catch (e: Exception) {
        log(TAG, "getTiers", e)
        context.internalError()
    }

    fun authenticateUser(context: Context) = try {
        context.rateLimit()

        val body = gson.fromJson(context.body(), AuthRequestBody::class.java)
        val authRequestResponse = requestAuthToken(body.code)
        DatabaseAccessor.saveAuthCredentials(body.goldId, authRequestResponse)
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
            val credentials = DatabaseAccessor.getAuthCredentials(goldId)
            credentials?.let { creds ->
                var validCredentials: AuthCredentials? = creds

                // Check expiration
                if (creds.expirationDate.before(ZonedDateTime.now(DATE_ZONE).toDate())) {
                    log(TAG, "Refreshing expired token")
                    val authRequestResponse = refreshAuthToken(refreshToken = creds.refreshToken)
                    DatabaseAccessor.saveAuthCredentials(goldId, authRequestResponse)
                    validCredentials = DatabaseAccessor.getAuthCredentials(goldId)
                }

                validCredentials?.let { validCreds ->
                    // Fetch user discord id
                    val discordId = getUserDiscordId(validCreds)

                    // Create user if needed
                    val rawUser = DatabaseAccessor.ensureUser(discordId)

                    FulguroBot.jda?.let { jda ->
                        val user = rawUser.cloneUserWithUpdatedProfile(jda, false)
                        if (user.name == user.discordId) {
                            context.notFoundError()  // User is not on the server
                        } else {
                            DatabaseAccessor.updateSimpleUser(user)
                            val profile = ApiProfile(user.discordId, user.name, user.avatar, validCreds.expirationDate)
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

        val request: Request = Request.Builder().url(Config.get("ladder.discord.auth.token.uri")).post(body).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = ApiException("DISCORD AUTH REQUEST FAILURE " + response.code)
            log(TAG, error.message!!, error)
            throw error
        }

        log(TAG, "DISCORD AUTH REQUEST SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun refreshAuthToken(refreshToken: String): AuthRequestResponse {
        val body: RequestBody = AuthRefreshPayload(refreshToken = refreshToken).toFormBody()
        val request: Request = Request.Builder().url(Config.get("ladder.discord.auth.token.uri")).post(body).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = ApiException("DISCORD AUTH REFRESH FAILURE " + response.code)
            log(TAG, error.message!!, error)
            throw error
        }

        log(TAG, "DISCORD AUTH REFRESH SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun getUserDiscordId(authCredentials: AuthCredentials): String {
        val url = "${Config.get("ladder.discord.api.url")}/users/@me"
        val request: Request = Request.Builder()
            .url(url)
            .header("Authorization", "${authCredentials.tokenType} ${authCredentials.accessToken}")
            .get().build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = ApiException("DISCORD PROFILE REQUEST FAILURE " + response.code)
            log(TAG, error.message!!, error)
            throw error
        }

        log(TAG, "DISCORD PROFILE REQUEST SUCCESS ${response.code}")
        val responseBody = response.body?.string()
        response.close()
        return gson.fromJson(responseBody, ProfileRequestResponse::class.java).id
    }

    fun isScanning(context: Context) = try {
        context.rateLimit()
        context.standardResponse(GameScanner.isScanning)
    } catch (e: Exception) {
        log(TAG, "getUserDiscordId", e)
        context.internalError()
    }

    fun getAccounts(context: Context) = try {
        context.rateLimit()
        context.standardResponse(UserAccount.values().filter { it != UserAccount.DISCORD })
    } catch (e: Exception) {
        log(TAG, "getAccounts", e)
        context.internalError()
    }

    fun link(context: Context) = try {
        context.rateLimit()

        // Param validation
        val body = gson.fromJson(context.body(), LinkRequestBody::class.java)
        val account = UserAccount.find(body.account)
        val accountId = body.accountId
        val discordId = body.discordId

        if (account == null || accountId.isBlank() || discordId.isBlank()) context.notFoundError()
        else {
            // Check that discord id exists
            val discordUser = DatabaseAccessor.user(UserAccount.DISCORD, discordId)
            if (discordUser == null) context.notFoundError()
            else if (account == UserAccount.DISCORD) context.internalError()
            else if (account == UserAccount.OGS) {
                val ogsId = (UserAccount.OGS.client as OgsClient).userIdFromPseudo(accountId)
                ogsId?.let { linkAccount(context, account, it, discordId) }
                    ?: context.notFoundError()
            } else linkAccount(context, account, accountId, discordId)
        }
    } catch (e: Exception) {
        log(TAG, "link", e)
        context.internalError()
    }

    private fun linkAccount(context: Context, account: UserAccount, accountId: String, discordId: String) {
        // Check that this account is free to link
        DatabaseAccessor.user(account, accountId)?.let {
            // Already linked
            context.internalError()
        } ?: run {
            val user = account.client.user(User.dummyFrom(discordId, account, accountId))

            user?.let {
                val realId = if (account == UserAccount.FOX) it.pseudo()!! else it.id()!!
                DatabaseAccessor.linkUserAccount(discordId, account, realId)

                // Update user info (pseudo / rank)
                FulguroBot.jda?.let { jda ->
                    DatabaseAccessor
                        .user(account, accountId)
                        ?.cloneUserWithUpdatedProfile(jda, true)
                        ?.let { updatedUser -> DatabaseAccessor.updateUser(updatedUser) }
                }

                context.standardResponse()
            } ?: context.notFoundError()
        }
    }

    fun unlink(context: Context) = try {
        context.rateLimit()

        // Param validation
        val body = gson.fromJson(context.body(), LinkRequestBody::class.java)
        val account = UserAccount.find(body.account)
        val accountId = body.accountId
        val discordId = body.discordId

        if (account == null || accountId.isBlank() || discordId.isBlank()) context.notFoundError()
        else {
            // Check that discord id exists
            val discordUser = DatabaseAccessor.user(UserAccount.DISCORD, discordId)
            if (discordUser == null) context.notFoundError()
            else {
                // Check that this account exists link
                DatabaseAccessor.user(account, accountId)?.let {
                    DatabaseAccessor.unlinkUserAccount(discordId, account, accountId)
                    context.standardResponse()
                } ?: run {
                    context.internalError()
                }
            }
        }
    } catch (e: Exception) {
        log(TAG, "unlink", e)
        context.internalError()
    }

    fun examRanking(context: Context) = try {
        context.rateLimit()
        context.standardResponse(DatabaseAccessor.examRanking())
    } catch (e: Exception) {
        log(TAG, "examRanking", e)
        context.internalError()
    }

    fun examTitles(context: Context) = try {
        context.rateLimit()
        val hunters = DatabaseAccessor.titledHunters()
        val titles = mutableListOf<ApiExamTitle>()
        ExamSpecialization.values().forEach { spec ->
            val specHunter = hunters.firstOrNull { spec.titleCountCallback(it) > 0 }
            titles.add(ApiExamTitle.from(specHunter, spec))
        }
        context.standardResponse(titles)
    } catch (e: Exception) {
        log(TAG, "examTitles", e)
        context.internalError()
    }

    fun examHistory(context: Context) = try {
        context.rateLimit()
        context.standardResponse(DatabaseAccessor.getPromotions().reversed())
    } catch (e: Exception) {
        log(TAG, "examHistory", e)
        context.internalError()
    }

    fun examStats(context: Context) = try {
        context.rateLimit()
        context.standardResponse(ApiExamStats.from(DatabaseAccessor.examStats()))
    } catch (e: Exception) {
        log(TAG, "examStats", e)
        context.internalError()
    }
}
