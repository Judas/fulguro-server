package com.fulgurogo.api

import com.fulgurogo.api.ApiModule.TAG
import com.fulgurogo.api.db.ApiDatabaseAccessor
import com.fulgurogo.api.db.model.*
import com.fulgurogo.api.link.AccountLinkers
import com.fulgurogo.api.utilities.badRequest
import com.fulgurogo.api.utilities.conflict
import com.fulgurogo.api.utilities.internalError
import com.fulgurogo.api.utilities.jsonResponse
import com.fulgurogo.api.utilities.notFoundError
import com.fulgurogo.api.utilities.rateLimit
import com.fulgurogo.api.utilities.standardResponse
import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.ServiceRegistry
import com.fulgurogo.common.utilities.DATE_ZONE
import com.fulgurogo.common.utilities.okHttpClient
import com.fulgurogo.common.utilities.toDate
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.fgc.db.FgcDatabaseAccessor
import com.fulgurogo.gold.db.GoldDatabaseAccessor
import com.fulgurogo.house.HouseSeason
import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.ogs.api.OgsApiClient
import com.google.gson.Gson
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import okhttp3.Request
import okhttp3.RequestBody
import java.time.ZonedDateTime

class Api {
    private val gson: Gson = Gson()
    private val accountLinkers = AccountLinkers(OgsApiClient())

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

    /**
     * The "Houses" page: the calendar, then the four houses with their RP, their size, their total and their leader.
     *
     * The clock is read once and that instant passed to both calendar questions, as everywhere else the two are asked
     * together: a period and a season read either side of midnight on 1 September would describe a season that is not
     * the one the points were summed over.
     */
    fun getHouses(context: Context) = context.handle("getHouses") {
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)
        val standings = HouseDatabaseAccessor.standings(season)
        context.standardResponse(ApiHouses.from(HouseSeason.period(now), season, standings))
    }

    /** One house's page: its RP, its figures and the ranking of its members. 404 on an unknown slug. */
    fun getHouse(context: Context) = context.handle("getHouse") {
        val slug = context.pathParam("slug")
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)

        val details = HouseDatabaseAccessor.details(season, slug)
        details?.let { context.standardResponse(ApiHouseDetails.from(HouseSeason.period(now), season, it)) }
            ?: context.notFoundError()
    }

    /**
     * Liveness of the background services. 200 when all of them are healthy, 503 otherwise, so a monitor can watch the
     * status code and only read the body when something is wrong.
     *
     * PingService keeps the *frontend* warm; nothing reported on the services themselves, which matters because a
     * wedged aggregator is otherwise completely silent.
     */
    fun getHealth(context: Context) = context.handle("getHealth") {
        val services = ServiceRegistry.health()
        // An empty registry means the modules never started, which is not healthy either
        val healthy = services.isNotEmpty() && services.all { it.healthy }
        context.jsonResponse(
            statusCode = if (healthy) 200 else 503,
            data = mapOf("healthy" to healthy, "services" to services)
        )
    }

    fun authenticateUser(context: Context) = context.handle("authenticateUser") {
        val body = gson.fromJson(context.body(), AuthRequestBody::class.java)
        val code = body?.code
        val goldId = body?.goldId
        if (code.isNullOrBlank() || goldId.isNullOrBlank()) {
            context.badRequest()
            return@handle
        }

        val authRequestResponse = requestAuthToken(code)
        ApiDatabaseAccessor.saveAuthCredentials(goldId, authRequestResponse)
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

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = Exception("DISCORD AUTH REQUEST FAILURE " + response.code)
                log(TAG, error.message!!, error)
                throw error
            }

            log(TAG, "DISCORD AUTH REQUEST SUCCESS ${response.code}")
            response.body.string()
        }
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun refreshAuthToken(refreshToken: String): AuthRequestResponse {
        val body: RequestBody = AuthRefreshPayload(refreshToken = refreshToken).toFormBody()
        val request: Request = Request.Builder().url(Config.get("gold.discord.auth.token.uri")).post(body).build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = Exception("DISCORD AUTH REFRESH FAILURE " + response.code)
                log(TAG, error.message!!, error)
                throw error
            }

            log(TAG, "DISCORD AUTH REFRESH SUCCESS ${response.code}")
            response.body.string()
        }
        return gson.fromJson(responseBody, AuthRequestResponse::class.java)
    }

    private fun getUserDiscordId(authCredentials: AuthCredentials): String {
        val url = "${Config.get("gold.discord.api.url")}/users/@me"
        val request: Request = Request.Builder()
            .url(url)
            .header("Authorization", "${authCredentials.tokenType} ${authCredentials.accessToken}")
            .get().build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = Exception("DISCORD PROFILE REQUEST FAILURE " + response.code)
                log(TAG, error.message!!, error)
                throw error
            }

            log(TAG, "DISCORD PROFILE REQUEST SUCCESS ${response.code}")
            response.body.string()
        }
        return gson.fromJson(responseBody, ProfileRequestResponse::class.java).id
    }

    fun getAccounts(context: Context) = context.handle("getAccounts") {
        context.standardResponse(accountLinkers.supportedServers)
    }

    fun link(context: Context) = context.handle("link") {
        // Param validation. Gson does not honour Kotlin nullability, so treat every field as possibly absent.
        val body = gson.fromJson(context.body(), LinkRequestBody::class.java)
        val discordId = body?.discordId
        val account = body?.account
        val accountId = body?.accountId
        if (discordId.isNullOrBlank() || account.isNullOrBlank() || accountId.isNullOrBlank()) {
            context.badRequest()
            return@handle
        }

        val linker = accountLinkers[account]
        if (linker == null) {
            context.badRequest()
            return@handle
        }

        // Check that discord id exists
        if (DiscordDatabaseAccessor.user(discordId) == null) {
            context.notFoundError()
            return@handle
        }

        val storedId = linker.resolveAccountId(accountId)
        if (storedId == null) {
            context.notFoundError()  // No such account on that platform
            return@handle
        }

        // Check if this account is free to link
        if (linker.isTaken(storedId)) {
            context.conflict()
            return@handle
        }

        linker.link(discordId, storedId)

        // Add in others DB
        GoldDatabaseAccessor.addPlayer(discordId)
        FgcDatabaseAccessor.addPlayer(discordId)
        context.standardResponse()
    }
}
