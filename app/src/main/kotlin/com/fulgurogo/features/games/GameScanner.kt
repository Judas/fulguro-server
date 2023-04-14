package com.fulgurogo.features.games

import com.fulgurogo.Config
import com.fulgurogo.features.database.DatabaseAccessor
import com.fulgurogo.features.user.User
import com.fulgurogo.features.user.UserAccount
import com.fulgurogo.features.user.UserAccountClient
import com.fulgurogo.utilities.*
import com.fulgurogo.utilities.Logger.Level.ERROR
import com.fulgurogo.utilities.Logger.Level.INFO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZonedDateTime

class GameScanner(
    private val listeners: List<GameScanListener>
) {
    var isScanning: Boolean = false
        private set
    var lastScanDate: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)
        private set
    var nextScanDate: ZonedDateTime = ZonedDateTime.now(DATE_ZONE)
        private set

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
        log(INFO, "Scan started !")
        isScanning = true

        listeners.forEach { it.onScanStarted() }
        handleAllUsers(this::handleUser)
        listeners.forEach { it.onScanFinished() }

        // Clean older games
        DatabaseAccessor.cleanGames()

        isScanning = false
        log(INFO, "Scan ended !")
    }

    private fun handleUser(user: User) {
        // Compute date interval for games
        val scanStart = user.lastGameScan ?: ZonedDateTime.now(DATE_ZONE).toStartOfMonth().toDate()
        val scanEnd = ZonedDateTime.now(DATE_ZONE).toDate()

        // Fetch all games in interval
        log(INFO, "Fetching games between $scanStart | $scanEnd")

        val clients = mutableListOf<UserAccountClient>()
        user.kgsId?.let { clients.add(UserAccount.KGS.client) }
        user.ogsId?.let { clients.add(UserAccount.OGS.client) }
        user.foxPseudo?.let { clients.add(UserAccount.FOX.client) }

        val allGames = clients.map { it.userGames(user, scanStart, scanEnd) }.flatten()
        log(INFO, "Fetched ${allGames.size} valid games !")

        // Save game in DB
        allGames.forEach {
            if (!DatabaseAccessor.existGame(it)) DatabaseAccessor.saveGame(it)
        }

        // Update user scan date
        DatabaseAccessor.updateUserScanDate(user.discordId, scanEnd)
    }
}
