package com.fulgurogo.features.games

import com.fulgurogo.Config
import com.fulgurogo.features.bot.FulguroBot
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.exam.ExamPointsService
import com.fulgurogo.features.ladder.LadderRatingsService
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.dv8tion.jda.api.JDA
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime

object GameScanner {
    var isScanning: Boolean = false
        private set
    private var lastScanDate: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)
    private var nextScanDate: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)

    private val scanFlow: Flow<ZonedDateTime> = flow {
        refreshScanDates()
        delay(nextScanDate.millisecondsFromNow() + 10000)
        while (true) {
            emit(nextScanDate)
            refreshScanDates()
            delay(nextScanDate.millisecondsFromNow() + 10000)
        }
    }
    private var scanJob: Job? = null
    private val scanFlowExceptionHandler = CoroutineExceptionHandler { _, e ->
        log(ERROR, "Error while scanning", e)
        stop()
        start()
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .followRedirects(true)
        .build()

    fun start() {
        log(INFO, "start")

        scanJob = CoroutineScope(Dispatchers.IO + scanFlowExceptionHandler).launch {
            log(INFO, "starting scan job")
            scanFlow.collect {
                log(INFO, "scanFlow tick")
                when {
                    isScanning -> log(INFO, "Skip scan, previous scan is still ongoing")
                    Config.DEV -> log(INFO, "Skip scan in developer mode")
                    else -> scan()
                }
            }
        }
    }

    fun stop() {
        log(INFO, "stop")
        scanJob?.let {
            log(INFO, "stopping scan job")
            it.cancel()
        }
    }

    private fun refreshScanDates() {
        lastScanDate = ZonedDateTime.now(DATE_ZONE)
        nextScanDate = with(lastScanDate) {
            var date = this.withMinute(0).withSecond(0).withNano(0)
            while (date.hour % 2 != 0 || date.isBefore(this))
                date = date.plusHours(1)
            date
        }

        log(INFO, "refreshScanDates")
        log(INFO, "  - Last scan at $lastScanDate")
        log(INFO, "  - Next scan at $nextScanDate")
    }

    fun scan() {
        FulguroBot.jda?.let { jda ->
            log(INFO, "Scan started !")
            isScanning = true

            handleAllUsers { rawUser ->
                val user = refreshUserProfile(jda, rawUser)
                updateUnfinishedGamesStatus(user)
                createUserExamLadder(user)
                fetchUserGames(user)
            }
            LadderRatingsService.refresh()
            ExamPointsService(jda).refresh()
            cleanDatabase()

            isScanning = false
            log(INFO, "Scan ended !")
        } ?: log(ERROR, "Can't start scan, JDA is null")
    }

    private fun refreshUserProfile(jda: JDA, rawUser: User): User =
        if (ZonedDateTime.now(DATE_ZONE).hour in 1..3) {
            log(INFO, "Refreshing user profile")
            val user = rawUser.cloneUserWithUpdatedProfile(jda, true)
            DatabaseAccessor.updateUser(user)
            if (user.name == user.discordId) {
                DatabaseAccessor.deleteUser(user.discordId)
                throw InvalidUserException
            }
            user
        } else rawUser

    private fun updateUnfinishedGamesStatus(user: User) {
        log(INFO, "Updating unfinished games status")
        DatabaseAccessor.unfinishedGamesFor(user.discordId)
            .forEach {
                // Check if game is now finished and update flag in db
                val updatedGame = UserAccount.find(it.server)?.client?.userGame(user, it.gameServerId())
                if (updatedGame?.isFinished() == true) DatabaseAccessor.updateFinishedGame(updatedGame)
            }
    }

    private fun createUserExamLadder(user: User) {
        log(INFO, "Creating exam player if needed")
        DatabaseAccessor.ensureExamPlayer(user)

        log(INFO, "Creating ladder player if needed")
        val ladderPlayer = DatabaseAccessor.ladderPlayer(user)
        if (ladderPlayer == null) DatabaseAccessor.createLadderPlayer(user.discordId)
    }

    private fun fetchUserGames(user: User) {
        val scanStart = user.lastGameScan ?: ZonedDateTime.now(DATE_ZONE).toStartOfMonth().toDate()
        val scanEnd = ZonedDateTime.now(DATE_ZONE).toDate()
        log(INFO, "Fetching user games between $scanStart | $scanEnd")

        val clients = mutableListOf<UserAccountClient>()
        user.kgsId?.let { clients.add(UserAccount.KGS.client) }
        user.ogsId?.let { clients.add(UserAccount.OGS.client) }
        user.foxPseudo?.let { clients.add(UserAccount.FOX.client) }
        val allGames = clients.map { it.userGames(user, scanStart, scanEnd) }.flatten()
        log(INFO, "Fetched ${allGames.size} valid games !")

        allGames.forEach { game ->
            if (!DatabaseAccessor.existGame(game)) {
                // Get players discord ids
                val blackPlayerDiscordId = DatabaseAccessor.user(game.account(), game.blackPlayerServerId())?.discordId
                val whitePlayerDiscordId = DatabaseAccessor.user(game.account(), game.whitePlayerServerId())?.discordId

                // Get game SGF
                val sgf: String? = if (blackPlayerDiscordId == null || whitePlayerDiscordId == null) null
                else game.sgfLink(blackPlayerDiscordId, whitePlayerDiscordId)?.let { sgfLink ->
                    log(INFO, "Fetching SGF $sgfLink")
                    val request: Request = Request.Builder().url(sgfLink).get().build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        log(INFO, "SGF fetch SUCCESS ${response.code}")
                        val apiResponse = response.body!!.string()
                        response.close()
                        apiResponse
                    } else {
                        val error = ApiException("SGF fetch FAILURE " + response.code)
                        log(ERROR, error.message!!, error)
                        null
                    }
                } ?: run {
                    log(INFO, "No valid SGF link for this game.")
                    null
                }

                // Saving game in DB
                DatabaseAccessor.saveGame(game, blackPlayerDiscordId, whitePlayerDiscordId, sgf)
            } else log(INFO, "Skipping. Game already exists in database")
        }
        DatabaseAccessor.updateUserScanDate(user.discordId, scanEnd)
    }

    private fun cleanDatabase() {
        log(INFO, "Deleting old games")
        DatabaseAccessor.cleanGames()
    }
}
