package com.fulgurogo.ogs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.common.utilities.rankToKyuDanString
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.ogs.OgsModule.TAG
import com.fulgurogo.ogs.api.OgsApiClient
import com.fulgurogo.ogs.api.model.OgsAuthCredentials
import com.fulgurogo.ogs.api.model.OgsAuthPayload
import com.fulgurogo.ogs.db.OgsDatabaseAccessor
import com.fulgurogo.ogs.db.model.OgsGame
import com.fulgurogo.ogs.websocket.OgsWsClient
import com.fulgurogo.ogs.websocket.model.*
import com.fulgurogo.ogs.websocket.model.GameListRequest.Companion.GAME_LIST_REQUEST_ID

class OgsRealTimeService : PeriodicFlowService(0, 10), OgsWsClient.Listener {
    private var processing = false
    private val webSocket = OgsWsClient(Config.get("ogs.websocket.url"), this)
    private var credentials: OgsAuthCredentials? = null

    override fun onTick() {
        if (processing) return
        processing = true

        if (webSocket.isOpen) {
            // Send ping to keep connection alive
            val ping = Request("net/ping", PingRequest())
            webSocket.send(ping.toString())

            // Request live games from gold players
            val ids = OgsDatabaseAccessor.allUserIds()
            val data = GameListRequest(where = GameListRequest.Filters(players = ids))
            val games = Request("gamelist/query", data, GAME_LIST_REQUEST_ID)
            webSocket.send(games.toString())
        } else {
            // Login via HTTP API (we need a jwt to authenticate to the RT API later)
            credentials = login()

            // Connect to OGS web socket
            if (credentials != null) webSocket.connect()
            else log(TAG, "FAILURE - Cannot authenticate to OGS")
        }

        processing = false
    }

    override fun onOpened() {
        log(TAG, "onOpened")

        credentials?.let {
            // Authenticate user via the WebSocket
            val auth = Request("authenticate", AuthRequest(it.jwt))
            webSocket.send(auth.toString())
        } ?: run {
            log(TAG, "onOpened invalid credentials")
            webSocket.close()
        }
    }

    override fun onClosed(reason: String?) {
        log(TAG, "onClosed $reason")
    }

    override fun onError(e: Exception?) {
        log(TAG, "onError ${e?.message}")
    }

    override fun onGameListResponse(message: OgsWsMessage.GameList) {
        message.data.results
            .filter {
                // Only keep games between 2 gold players
                val existsBlack = OgsDatabaseAccessor.user(it.black.id) != null
                val existsWhite = OgsDatabaseAccessor.user(it.white.id) != null
                existsBlack && existsWhite
            }
            .filter { it.width == it.height } // Skip non-square goban
            .filterNot { it.rengo } // Skip rengo
            .map { it.id }
            .forEach {
                // Connect to the games to receive updates, we can connect multiple times
                val connect = Request("game/connect", GameConnectRequest(it))
                webSocket.send(connect.toString())
                // We should then receive a game/id/gamedata update with full game object
            }
    }

    override fun onGameDataUpdate(message: OgsWsMessage.GameData) {
        val gameData = message.data

        // Skip weird result
        val result = gameData.result()
        if (result == null) return

        // Fetch SGF
        val sgf = fetchSgf(gameData.id)

        // Make it a DB game
        val game = OgsGame(
            goldId = gameData.goldId(),
            id = gameData.id,
            date = gameData.date(),
            blackId = gameData.players.black.id,
            blackName = gameData.players.black.username,
            blackRank = gameData.players.black.rank.rankToKyuDanString(),
            whiteId = gameData.players.white.id,
            whiteName = gameData.players.white.username,
            whiteRank = gameData.players.white.rank.rankToKyuDanString(),
            size = gameData.width,
            komi = gameData.komi.toDouble(),
            handicap = gameData.handicap,
            longGame = gameData.isLongGame(),
            result = result,
            sgf = sgf
        )

        // Check corresponding game in DB
        val dbGame = OgsDatabaseAccessor.game(game)
        val existsInDb = dbGame != null
        val finishedInDb = dbGame != null && dbGame.isFinished()

        if (gameData.isFinished() && !finishedInDb) {
            // Update game in DB
            if (existsInDb) OgsDatabaseAccessor.finishGame(game)
            else OgsDatabaseAccessor.addGame(game)

            // Notify on Discord
            notifyGame(game)
        } else if (!game.isFinished() && !existsInDb) {
            // Add game in DB
            OgsDatabaseAccessor.addGame(game)

            // Notify on Discord
            notifyGame(game)
        }
    }

    private fun login(): OgsAuthCredentials? = try {
        val route = "${Config.get("ogs.auth.api.url")}/login"
        val body = OgsAuthPayload(
            Config.get("ogs.auth.username"),
            Config.get("ogs.auth.password")
        )
        OgsApiClient.post(route, body, OgsAuthCredentials::class.java)
    } catch (_: Exception) {
        null
    }

    private fun fetchSgf(id: Int): String = try {
        OgsApiClient.get("${Config.get("ogs.api.url")}/games/$id/sgf")
    } catch (_: Exception) {
        ""
    }

    private fun notifyGame(game: OgsGame) {
        val title = ":popcorn: Partie ${if (game.isFinished()) "terminée" else "en cours"} sur OGS !"
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = game.description(),
            title = title
        )
    }
}
