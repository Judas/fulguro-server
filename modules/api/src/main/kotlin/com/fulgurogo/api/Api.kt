package com.fulgurogo.api

import com.fulgurogo.api.ApiModule.TAG
import com.fulgurogo.api.db.ApiDatabaseAccessor
import com.fulgurogo.api.db.model.*
import com.fulgurogo.api.league.LeagueApiComposer
import com.fulgurogo.api.link.AccountLinkers
import com.fulgurogo.api.link.FoxApiClient
import com.fulgurogo.api.utilities.badRequest
import com.fulgurogo.api.utilities.conflict
import com.fulgurogo.api.utilities.forbidden
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
import com.fulgurogo.house.HouseAction
import com.fulgurogo.house.HouseNotifier
import com.fulgurogo.house.HousePeriod
import com.fulgurogo.house.HouseRoles
import com.fulgurogo.house.HouseSeason
import com.fulgurogo.house.db.HouseDatabaseAccessor
import com.fulgurogo.house.db.model.House
import com.fulgurogo.league.LeagueSession
import com.fulgurogo.league.db.LeagueDatabaseAccessor
import com.fulgurogo.ogs.api.OgsApiClient
import com.google.gson.Gson
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import okhttp3.Request
import okhttp3.RequestBody
import java.time.ZonedDateTime

class Api {
    private val gson: Gson = Gson()
    private val accountLinkers = AccountLinkers(OgsApiClient(), FoxApiClient())

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

    /**
     * Every player of the ladder, each with the badge of the house they belong to.
     *
     * Two extra reads for the whole list, not two per player: [HouseDatabaseAccessor.members] takes the ids in one
     * batch — it was written for the scanner, for this same reason — and the four houses are read once and kept in a
     * map. A membership naming a house that does not exist yields a null crest rather than failing the response,
     * the same call the league composer makes.
     *
     * The crest and not [ApiPlayerHouse]: a roster of every player has no use for four houses' worth of tagline,
     * description, points breakdown and rank repeated down it. That block stays on the profile route.
     */
    fun getPlayers(context: Context) = context.handle("getPlayers") {
        val players = ApiDatabaseAccessor.apiPlayers()
        val memberships = HouseDatabaseAccessor.members(players.map { it.discordId })
        val crests = HouseDatabaseAccessor.houses().associate { it.id to ApiHouseCrest.from(it) }

        players.forEach { player -> player.crest = memberships[player.discordId]?.houseId?.let { crests[it] } }

        context.standardResponse(players)
    }

    /**
     * One player's profile: their accounts, their rating, their games, and their house when they have one.
     *
     * The house block is composed here rather than added to the `api_players` view. It is counted over the current season,
     * which is computed in Kotlin and cannot be handed to a view, and keeping it out means no view to alter on the
     * production server.
     */
    fun getPlayerProfile(context: Context) = context.handle("getPlayerProfile") {
        val playerId = context.pathParam("id")
        val player = ApiDatabaseAccessor.apiPlayer(playerId)
        player?.let { p ->
            p.games = ApiDatabaseAccessor.apiGamesFor(playerId)

            // One clock read for both calendar questions, as everywhere else: a period and a season taken either side of
            // midnight on 1 September would describe a season other than the one the points were summed over.
            val now = ZonedDateTime.now(DATE_ZONE)
            val season = HouseSeason.seasonName(now)
            p.house = HouseDatabaseAccessor.playerStanding(season, playerId)
                ?.let { ApiPlayerHouse.from(HouseSeason.period(now), season, it) }
            // Same story as the house block: counted over the current season, which only Kotlin knows, so it is composed
            // here rather than added to `api_players` — and there is no view to alter on the production server.
            p.league = LeagueApiComposer(season).playerBlock(playerId, HouseSeason.period(now))

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
     * Joins a house: 400 on a bad body, 403 during the summer break, 404 on an unknown player, one with no linked
     * account or an unknown house, 409 when they already have a house, 200 with the house they joined.
     *
     * The player picks their own house — the body names it by slug and nothing balances the four. Nothing identifies the
     * caller either, as with `link`: the id in the body is taken as it comes, so anyone can join anyone into any house.
     *
     * The 409 is answered twice over. The read is what makes it the normal answer, and [HouseDatabaseAccessor.addMember]
     * repeats it from the primary key, which is the only one of the two that holds when two joins for the same player
     * land at once.
     */
    fun joinHouse(context: Context) = context.handle("joinHouse") {
        // Gson does not honour Kotlin nullability, so treat every field as possibly absent.
        val body = gson.fromJson(context.body(), HouseJoinRequestBody::class.java)
        val discordId = body?.discordId
        val slug = body?.slug
        if (discordId.isNullOrBlank() || slug.isNullOrBlank()) {
            context.badRequest()
            return@handle
        }

        if (HouseSeason.period() == HousePeriod.VACATION) {
            context.forbidden()  // No joining during the break: the choices of the summer are what move people
            return@handle
        }

        // Known to the server, and eligible: a Discord account alone is not enough, one platform account must be linked
        if (DiscordDatabaseAccessor.user(discordId) == null || GoldDatabaseAccessor.player(discordId) == null) {
            context.notFoundError()
            return@handle
        }

        if (HouseDatabaseAccessor.member(discordId) != null) {
            context.conflict()
            return@handle
        }

        // Read last, where the draw used to be, so a player who already has a house is told that rather than being sent
        // to check their slug. A slug naming no house is a 404 like an unknown player, for the same reason -- the body
        // points at something that does not exist -- and logged apart, since the two answers look alike from outside.
        val house = HouseDatabaseAccessor.house(slug)
        if (house == null) {
            log(TAG, "joinHouse $discordId asked for unknown house $slug")
            context.notFoundError()
            return@handle
        }

        if (!HouseDatabaseAccessor.addMember(discordId, house.id)) {
            context.conflict()
            return@handle
        }

        log(TAG, "joinHouse $discordId joined ${house.slug}")
        // Announced and dressed only past the write that decided it, and only by the caller that won it -- the addMember
        // guard above is what makes two simultaneous joins produce one arrival message and one role grant, not two.
        HouseNotifier.notifyArrival(discordId, house)
        HouseRoles.grant(discordId, house)
        context.standardResponse(ApiHouseIdentity.from(house))
    }

    /**
     * Records what a member wants for next season: 400 on a bad body, an unknown action, a `CHANGE` with no house or one
     * naming the house they are already in, 403 outside the summer break, 404 when the player has no house or the house
     * they named does not exist, 204 once recorded.
     *
     * A `CHANGE` carries its destination, since there is no draw left to invent one: the slug is required on that action
     * and ignored on the other two, which have nowhere to go.
     *
     * Nothing is applied here — the season transition reads these back on 1 September. A choice can therefore be changed
     * as often as the player likes all summer, the last one recorded being the one that counts, destination included.
     */
    fun setHouseChoice(context: Context) = context.handle("setHouseChoice") {
        val body = gson.fromJson(context.body(), HouseChoiceRequestBody::class.java)
        val discordId = body?.discordId
        val action = HouseAction.from(body?.action)
        if (discordId.isNullOrBlank() || action == null) {
            context.badRequest()
            return@handle
        }

        if (HouseSeason.period() != HousePeriod.VACATION) {
            context.forbidden()  // During the season, a membership is settled: only the break moves people
            return@handle
        }

        // The existence check the write cannot make: an UPDATE reports 0 rows both for an unknown member and for a
        // choice already set to that value, so a 404 read off it would fire on recording the same action twice. The
        // membership is also what a CHANGE is checked against, so it is read before the destination either way.
        val member = HouseDatabaseAccessor.member(discordId)
        if (member == null) {
            context.notFoundError()
            return@handle
        }

        // Only a CHANGE has a destination, and it must be one: an absent slug is a 400 and an unknown one a 404, the
        // same two answers `join` gives. Naming the house they are already in is a 400 rather than a quiet STAY --
        // recording a change that changes nothing would read on the profile as a move that never comes.
        var house: House? = null
        if (action == HouseAction.CHANGE) {
            val slug = body?.slug
            if (slug.isNullOrBlank()) {
                context.badRequest()
                return@handle
            }

            house = HouseDatabaseAccessor.house(slug)
            if (house == null) {
                log(TAG, "setHouseChoice $discordId asked for unknown house $slug")
                context.notFoundError()
                return@handle
            }

            if (house.id == member.houseId) {
                log(TAG, "setHouseChoice $discordId asked to change to their own house $slug")
                context.badRequest()
                return@handle
            }
        }

        HouseDatabaseAccessor.setPendingAction(discordId, action.name, house?.id)
        log(TAG, "setHouseChoice $discordId chose $action${house?.let { " to ${it.slug}" } ?: ""}")
        context.standardResponse()
    }

    /**
     * The "League" page: the calendar, then every member of the season best first.
     *
     * The clock is read once and that instant passed to both calendar questions, as everywhere the two are asked together:
     * a period and a season read either side of midnight on 1 September would describe a season other than the one the
     * standings were summed over.
     *
     * `currentSession` is null out of season and inside the two holes of the calendar. That is the answer, not a gap.
     */
    fun getLeague(context: Context) = context.handle("getLeague") {
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)
        val composer = LeagueApiComposer(season)
        val current = LeagueSession.current(season, now)

        context.standardResponse(
            ApiLeague(
                season = season,
                period = HouseSeason.period(now),
                sessionCount = composer.sessionCount,
                currentSession = current?.let { composer.session(it) },
                sessions = composer.calendar(),
                standings = composer.standings()
            )
        )
    }

    /**
     * One session's pairings, and the players the draw could not pair. 404 on a number that is not a session of the season.
     *
     * The 404 is read off the calendar rather than off a range check: the season is what says how many sessions there are,
     * so a hardcoded 1..16 would answer 404 for a legitimate session the day the split changed.
     *
     * A session with no matches is not an error. Not yet drawn, or drawn with nobody to pair — `session.drawn` tells those
     * apart, and the exemptions are what make the second case legible.
     */
    fun getLeagueSession(context: Context) = context.handle("getLeagueSession") {
        val number = context.pathParam("number").toIntOrNull()
        val now = ZonedDateTime.now(DATE_ZONE)
        val season = HouseSeason.seasonName(now)

        val session = LeagueSession.sessions(season).firstOrNull { it.number == number }
        if (session == null) {
            context.notFoundError()
            return@handle
        }

        val composer = LeagueApiComposer(season)
        context.standardResponse(
            ApiLeagueSessionDetails(
                season = season,
                period = HouseSeason.period(now),
                sessionCount = composer.sessionCount,
                session = composer.session(session),
                matches = composer.sessionMatches(session.number),
                exemptions = composer.sessionExemptions(session.number)
            )
        )
    }

    /**
     * Joins the league: 400 on a bad body, 403 outside the season, 404 on an unknown player or one with no house or no
     * linked OGS account, 409 when they are already an active member, 200 with where they now stand.
     *
     * Two writes, and no network call. The `PUT member/{id}` OGS needs is left to the tick, for two reasons: joining must
     * not fail because OGS is momentarily down, and this handler would otherwise be the second place in the project where
     * an outbound request blocks an inbound one.
     *
     * A player who joined, left and came back lands on their existing row with `active` back to 1, which is why this is an
     * `INSERT IGNORE` followed by [LeagueDatabaseAccessor.setActive] rather than a bare insert. Their `joined` is not
     * restamped and the renown they already earned stays on their matches.
     *
     */
    fun joinLeague(context: Context) = context.handle("joinLeague") {
        // Gson does not honour Kotlin nullability, so treat every field as possibly absent.
        val body = gson.fromJson(context.body(), LeagueMembershipRequestBody::class.java)
        val discordId = body?.discordId
        if (discordId.isNullOrBlank()) {
            context.badRequest()
            return@handle
        }

        if (HouseSeason.period() == HousePeriod.VACATION) {
            context.forbidden()  // The academies are formed at the start of a season, not during the break
            return@handle
        }

        // The three eligibility conditions, in the order the plan words them
        val known = DiscordDatabaseAccessor.user(discordId) != null
        val housed = HouseDatabaseAccessor.member(discordId) != null
        if (!known || !housed || !LeagueDatabaseAccessor.isLinkedToOgs(discordId)) {
            context.notFoundError()
            return@handle
        }

        val season = HouseSeason.seasonName()
        // Only an *active* membership is a conflict: an inactive one is exactly what a returning player rejoins.
        if (LeagueDatabaseAccessor.member(season, discordId)?.active == true) {
            context.conflict()
            return@handle
        }

        val created = LeagueDatabaseAccessor.addMember(season, discordId)
        LeagueDatabaseAccessor.setActive(season, discordId, true)
        // Their OGS row survives leaving and rejoining, so this only ever creates one the first time round.
        LeagueDatabaseAccessor.addPlayer(discordId)

        val member = LeagueDatabaseAccessor.member(season, discordId)
        if (member == null) {
            // The row was written and cannot be read back: nothing the caller did, and nothing they can fix.
            log(TAG, "joinLeague FAILURE $discordId membership not readable after write")
            context.internalError()
            return@handle
        }

        log(TAG, "joinLeague $discordId ${if (created) "joined" else "rejoined"} the league for $season")
        val registered = LeagueDatabaseAccessor.player(discordId)?.ogsRegistered != null
        context.standardResponse(ApiLeagueMembership.from(member, registered))
    }

    /**
     * Leaves the league: 400 on a bad body, 404 when the player is not a member of this season, 204 once recorded.
     *
     * Unlike the houses, leaving is possible **during** the season. Nothing is withdrawn on the OGS side — a
     * `DELETE /member/{id}` does exist, but a player is meant to stay in the OGS league — and nothing is withdrawn here
     * either: a player who leaves mid-session keeps the match already drawn for them. They are free to play it, and if
     * they do not, the unplayed rule applies to them as to everyone.
     *
     * No period check, unlike [joinLeague]: there is nothing to protect against out of season, and refusing would leave a
     * player who wants out waiting until September.
     *
     * The log line names the player on purpose. With no identity check on the body this is the only trace of who was
     * dropped and when, and it is what makes a sabotage reconstructible after the fact.
     */
    fun leaveLeague(context: Context) = context.handle("leaveLeague") {
        val body = gson.fromJson(context.body(), LeagueMembershipRequestBody::class.java)
        val discordId = body?.discordId
        if (discordId.isNullOrBlank()) {
            context.badRequest()
            return@handle
        }

        val season = HouseSeason.seasonName()
        // The existence check the write cannot make: an UPDATE reports 0 rows both for an unknown member and for one
        // already inactive, so a 404 read off it would fire on leaving twice.
        if (LeagueDatabaseAccessor.member(season, discordId) == null) {
            context.notFoundError()
            return@handle
        }

        LeagueDatabaseAccessor.setActive(season, discordId, false)
        log(TAG, "leaveLeague $discordId left the league for $season")
        context.standardResponse()
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

        val resolvedAccount = linker.resolveAccount(accountId)
        if (resolvedAccount == null) {
            context.notFoundError()  // No such account on that platform
            return@handle
        }

        // Check if this account is free to link
        if (linker.isLinked(discordId) || linker.isTaken(resolvedAccount)) {
            context.conflict()
            return@handle
        }

        linker.link(discordId, resolvedAccount)

        // Add in others DB
        GoldDatabaseAccessor.addPlayer(discordId)
        FgcDatabaseAccessor.addPlayer(discordId)
        context.standardResponse()
    }
}
