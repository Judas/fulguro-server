package com.fulgurogo.features.games

import com.fulgurogo.common.Config
import com.fulgurogo.features.bot.FulguroBot
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.exam.ExamPointsService
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.features.user.UserAccountGame
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
import java.util.*

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
                    Config.get("debug").toBoolean() -> log(INFO, "Skip scan in developer mode")
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

            forAllUsers { rawUser ->
                val user = refreshUserProfile(jda, rawUser)
                createExamPlayer(user)
                updateUnfinishedGamesStatus(user)

                val scanStart = user.lastGameScan ?: ZonedDateTime.now(DATE_ZONE).toStartOfMonth().toDate()
                val scanEnd = ZonedDateTime.now(DATE_ZONE).toDate()
                val userGames = fetchUserGames(user, scanStart, scanEnd)
                saveUserGames(userGames)

                // Update user last game scan date
                DatabaseAccessor.updateUserScanDate(user.discordId, scanEnd)
            }
            ExamPointsService(jda).refresh()
            cleanDatabase()

            isScanning = false
            log(INFO, "Scan ended !")
        } ?: log(ERROR, "Can't start scan, JDA is null")
    }

    private fun refreshUserProfile(jda: JDA, rawUser: User): User {
        log(INFO, "Refreshing user profile")

        // Refresh server pseudos and ranks
        val user = rawUser.cloneUserWithUpdatedProfile(jda, true)
        DatabaseAccessor.updateUser(user)

        if (user.name == user.discordId) {
            DatabaseAccessor.deleteUser(user.discordId)
            throw InvalidUserException
        }
        return user
    }

    private fun createExamPlayer(user: User) {
        log(INFO, "Creating exam player if needed")
        DatabaseAccessor.ensureExamPlayer(user)
    }

    private fun fetchUserGames(user: User, scanStart: Date, scanEnd: Date): List<UserAccountGame> {
        log(INFO, "Fetching user games between $scanStart | $scanEnd")

        val clients = mutableListOf<UserAccountClient>()
        user.kgsId?.let { clients.add(UserAccount.KGS.client) }
        user.ogsId?.let { clients.add(UserAccount.OGS.client) }
        user.foxPseudo?.let { clients.add(UserAccount.FOX.client) }
        val allGames = clients.map { it.userGames(user, scanStart, scanEnd) }.flatten()
        log(INFO, "Fetched ${allGames.size} valid games !")

        return allGames
    }

    private fun updateUnfinishedGamesStatus(user: User) {
        log(INFO, "Checking unfinished games")
        DatabaseAccessor.unfinishedGamesFor(user.discordId)
            .forEach { game ->
                // Check if game is now finished and update flag in db
                val updatedGame = UserAccount.find(game.server)?.client?.userGame(user, game.gameServerId())
                if (updatedGame?.isFinished() == true) {
                    log(INFO, "Updating game as it is now finished")
                    val blackPlayerDiscordId =
                        DatabaseAccessor.user(updatedGame.account(), updatedGame.blackPlayerServerId())?.discordId
                    val whitePlayerDiscordId =
                        DatabaseAccessor.user(updatedGame.account(), updatedGame.whitePlayerServerId())?.discordId
                    val sgf = fetchGameSgf(updatedGame, blackPlayerDiscordId, whitePlayerDiscordId)
                    DatabaseAccessor.updateFinishedGame(updatedGame, sgf)
                }
            }
    }

    private fun saveUserGames(userGames: List<UserAccountGame>) {
        userGames.forEach { game ->
            if (!DatabaseAccessor.existGame(game)) {
                val blackPlayerDiscordId = DatabaseAccessor.user(game.account(), game.blackPlayerServerId())?.discordId
                val whitePlayerDiscordId = DatabaseAccessor.user(game.account(), game.whitePlayerServerId())?.discordId
                val sgf = fetchGameSgf(game, blackPlayerDiscordId, whitePlayerDiscordId)
                DatabaseAccessor.saveGame(game, blackPlayerDiscordId, whitePlayerDiscordId, sgf)
            } else log(INFO, "Skipping. Game already exists in database")
        }
    }

    private fun fetchGameSgf(
        game: UserAccountGame,
        blackPlayerDiscordId: String?,
        whitePlayerDiscordId: String?,
        allowRetry: Boolean = true
    ): String? {
        if (blackPlayerDiscordId == null || whitePlayerDiscordId == null) {
            log(INFO, "Fetching SGF: Skipping because player id is null")
            return null
        }

        if (!game.isFinished()) {
            log(INFO, "Fetching SGF: Skipping because game is ongoing")
            return null
        }

        val sgfLink = game.sgfLink(blackPlayerDiscordId, whitePlayerDiscordId)

        if (sgfLink == null) {
            log(INFO, "Fetching SGF: Skipping because no valid link")
            return null
        }

        log(INFO, "Fetching SGF $sgfLink")
        val request: Request = Request.Builder().url(sgfLink).get().build()
        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            log(INFO, "Fetching SGF SUCCESS ${response.code}")
            val apiResponse = response.body!!.string().replace("\n", "")
            response.close()
            apiResponse
        } else {
            if (response.code == 429 && allowRetry) {
                log(INFO, "Fetching SGF ERROR 429: Waiting then retrying")
                Thread.sleep(Config.get("ogs.api.delay.seconds").toInt() * 1000L)
                fetchGameSgf(game, blackPlayerDiscordId, whitePlayerDiscordId, false)
            } else {
                val error = ApiException("Fetching SGF FAILURE " + response.code)
                log(ERROR, error.message!!, error)
                null
            }
        }
    }

    private fun cleanDatabase() {
        log(INFO, "Deleting old games")
        DatabaseAccessor.cleanGames()
    }
}
