package com.fulgurogo.api

import com.fulgurogo.api.admin.LogReader
import com.fulgurogo.api.auth.SessionResolution
import com.fulgurogo.api.auth.SessionResolver
import com.fulgurogo.api.db.model.ApiOgsMember
import com.fulgurogo.ogs.OgsLiveSnapshot
import com.fulgurogo.ogs.websocket.model.OgsWsGameLight
import com.fulgurogo.ogs.websocket.model.OgsWsGameLightPlayer
import io.javalin.Javalin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveGamesApiTest {
    private val member = ApiOgsMember(1, "d1", "Member", "avatar", 1500.0, 3, "Tier")
    private val otherMember = ApiOgsMember(2, "d2", "Other", "avatar", 1400.0, 2, "Tier")

    @Test
    fun `no list yet is unavailable`() {
        withApi(null) { port -> assertEquals(503, request(port).statusCode()) }
    }

    @Test
    fun `stale list is unavailable`() {
        withApi(OgsLiveSnapshot(listOf(), Instant.now().minusSeconds(120))) { port ->
            assertEquals(503, request(port).statusCode())
        }
    }

    @Test
    fun `empty list is an empty answer`() {
        withApi(OgsLiveSnapshot(listOf(), Instant.now())) { port ->
            val response = request(port)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"games\":[]"))
        }
    }

    @Test
    fun `keeps games with one member and drops the rest`() {
        val games = listOf(
            game(10, black = 1, white = 99),             // one member: kept
            game(11, black = 98, white = 99),            // no member (unlinked since): dropped
            game(12, black = 1, white = 99, rengo = true), // rengo: dropped
            game(13, black = 1, white = 99, height = 13), // non-square: dropped
        )
        withApi(OgsLiveSnapshot(games, Instant.now())) { port ->
            val response = request(port)
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"goldId\":\"OGS_10\""))
            assertTrue(body.contains("\"link\":\"https://ogs.test/game/10\""))
            assertTrue(body.contains("\"discordName\":\"Member\""))
            assertTrue(body.contains("\"ogsRank\":\"1d\""))
            assertFalse(body.contains("OGS_11"))
            assertFalse(body.contains("OGS_12"))
            assertFalse(body.contains("OGS_13"))
        }
    }

    @Test
    fun `most recent first and flagged gold when both players are members`() {
        val games = listOf(
            game(20, black = 1, white = 99), // one member, older
            game(30, black = 1, white = 2),  // two members, newer
        )
        withApi(OgsLiveSnapshot(games, Instant.now())) { port ->
            val body = request(port).body()
            val newer = body.indexOf("\"goldId\":\"OGS_30\"")
            val older = body.indexOf("\"goldId\":\"OGS_20\"")
            assertTrue(newer in 0 until older, body)
            assertTrue(Regex("\"goldId\":\"OGS_30\"[^}]*\"gold\":true").containsMatchIn(body), body)
            assertTrue(Regex("\"goldId\":\"OGS_20\"[^}]*\"gold\":false").containsMatchIn(body), body)
        }
    }

    @Test
    fun `gold parameter filters on the flag`() {
        val games = listOf(game(20, black = 1, white = 99), game(30, black = 1, white = 2))
        withApi(OgsLiveSnapshot(games, Instant.now())) { port ->
            val all = request(port).body()
            assertTrue(all.contains("OGS_20") && all.contains("OGS_30"), all)

            val gold = request(port, "?gold=true").body()
            assertTrue(gold.contains("OGS_30") && !gold.contains("OGS_20"), gold)

            val notGold = request(port, "?gold=false").body()
            assertTrue(notGold.contains("OGS_20") && !notGold.contains("OGS_30"), notGold)

            assertEquals(400, request(port, "?gold=yes").statusCode())
        }
    }

    private fun game(id: Int, black: Int, white: Int, rengo: Boolean = false, height: Int = 19) = OgsWsGameLight(
        id = id,
        black = OgsWsGameLightPlayer(black, "p$black", 30.0),
        white = OgsWsGameLightPlayer(white, "p$white", 25.0),
        width = 19,
        height = height,
        rengo = rengo,
        moveNumber = 42,
    )

    private fun withApi(snapshot: OgsLiveSnapshot?, assertion: (Int) -> Unit) {
        val api = Api(
            sessionResolver = SessionResolver { SessionResolution.Unauthorized },
            logReader = LogReader { emptyList() },
            liveGames = { snapshot },
            ogsMembers = { ids -> listOf(member, otherMember).filter { it.ogsId in ids }.associateBy { it.ogsId } },
            ogsWebsiteUrl = { "https://ogs.test" },
        )
        val app = Javalin.create().get("/gold/api/games/live", api::getLiveGames).start(0)
        try {
            assertion(app.port())
        } finally {
            app.stop()
        }
    }

    private fun request(port: Int, suffix: String = ""): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://localhost:$port/gold/api/games/live$suffix")).GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )
}
