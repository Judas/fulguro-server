package com.fulgurogo.api

import com.fulgurogo.api.admin.LeagueAdministrator
import com.fulgurogo.api.admin.LogReader
import com.fulgurogo.api.auth.DiscordSession
import com.fulgurogo.api.auth.SessionResolution
import com.fulgurogo.api.auth.SessionResolver
import com.fulgurogo.api.db.model.ApiLeagueMatch
import com.fulgurogo.api.db.model.ApiLeagueMember
import com.fulgurogo.league.db.model.LeagueAward
import io.javalin.Javalin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminLeagueApiTest {
    private val adminRoles = setOf("admin-role")

    /** Records what reached the service, so a test can assert that a rejected request never did. */
    private class RecordingAdministrator(
        private val member: Boolean = true,
        private val matchExists: Boolean = true,
    ) : LeagueAdministrator {
        var removed: String? = null
        var ruled: Triple<Int, String, Pair<LeagueAward, LeagueAward>?>? = null
        var rulingCalls = 0

        override fun remove(discordId: String): Boolean {
            removed = discordId
            return member
        }

        override fun adjudicate(
            session: Int,
            blackDiscordId: String,
            awards: Pair<LeagueAward, LeagueAward>?,
            adminDiscordId: String
        ): ApiLeagueMatch? {
            rulingCalls++
            ruled = Triple(session, blackDiscordId, awards)
            if (!matchExists) return null
            return ApiLeagueMatch(
                black = ApiLeagueMember.unknown(blackDiscordId),
                white = ApiLeagueMember.unknown("white"),
                result = ApiLeagueMatch.ADJUDICATED
            )
        }
    }

    // Remove

    @Test
    fun `remove needs a session and the role`() {
        withApi(SessionResolution.Unauthorized) { port -> assertEquals(401, post(port, REMOVE, null, REMOVE_BODY).statusCode()) }
        withApi(SessionResolution.Unavailable) { port -> assertEquals(503, post(port, REMOVE, "s", REMOVE_BODY).statusCode()) }
        withApi(authenticated(setOf("other"))) { port -> assertEquals(403, post(port, REMOVE, "s", REMOVE_BODY).statusCode()) }
    }

    @Test
    fun `remove rejects a blank id without touching the league`() {
        val admin = RecordingAdministrator()
        withApi(authenticated(adminRoles), admin) { port ->
            assertEquals(400, post(port, REMOVE, "s", """{"discordId":" "}""").statusCode())
        }
        assertNull(admin.removed)
    }

    @Test
    fun `remove of a non member is not found`() {
        withApi(authenticated(adminRoles), RecordingAdministrator(member = false)) { port ->
            assertEquals(404, post(port, REMOVE, "s", REMOVE_BODY).statusCode())
        }
    }

    @Test
    fun `admin removes the member named in the body`() {
        val admin = RecordingAdministrator()
        withApi(authenticated(adminRoles), admin) { port ->
            assertEquals(204, post(port, REMOVE, "s", REMOVE_BODY).statusCode())
        }
        assertEquals("42", admin.removed)
    }

    // Adjudicate

    @Test
    fun `adjudicate needs the role`() {
        withApi(authenticated(setOf("other"))) { port ->
            assertEquals(403, post(port, ADJUDICATE, "s", ruling("FORFEIT", "WINNER")).statusCode())
        }
    }

    @Test
    fun `malformed rulings are bad requests and never reach the league`() {
        val bodies = listOf(
            "{}",
            """{"blackDiscordId":"b","blackAward":"FORFEIT","whiteAward":"WINNER"}""",  // no session
            """{"session":3,"blackAward":"FORFEIT","whiteAward":"WINNER"}""",  // no match
            ruling("FORFEIT", null),  // one side only
            ruling(null, "WINNER"),
            ruling("forfeit", "WINNER"),  // case matters
            ruling("LOSER", "WINNER"),
            ruling("WINNER", "WINNER"),  // two winners
        )
        for (body in bodies) {
            val admin = RecordingAdministrator()
            withApi(authenticated(adminRoles), admin) { port ->
                assertEquals(400, post(port, ADJUDICATE, "s", body).statusCode(), body)
            }
            assertEquals(0, admin.rulingCalls, body)
        }
    }

    @Test
    fun `unknown match is not found`() {
        withApi(authenticated(adminRoles), RecordingAdministrator(matchExists = false)) { port ->
            assertEquals(404, post(port, ADJUDICATE, "s", ruling("FORFEIT", "WINNER")).statusCode())
        }
    }

    @Test
    fun `admin rules freely per side`() {
        for ((black, white) in listOf(
            "FORFEIT" to "EXEMPT", "FORFEIT" to "PARTICIPANT", "WINNER" to "FORFEIT", "EXEMPT" to "EXEMPT",
            "PARTICIPANT" to "WINNER",
        )) {
            val admin = RecordingAdministrator()
            withApi(authenticated(adminRoles), admin) { port ->
                val response = post(port, ADJUDICATE, "s", ruling(black, white))
                assertEquals(200, response.statusCode())
                assertTrue(response.body().contains(""""result":"adjudicated""""), response.body())
            }
            assertEquals(Triple(3, "b", LeagueAward.valueOf(black) to LeagueAward.valueOf(white)), admin.ruled)
        }
    }

    @Test
    fun `both awards null withdraws the ruling`() {
        val admin = RecordingAdministrator()
        withApi(authenticated(adminRoles), admin) { port ->
            assertEquals(200, post(port, ADJUDICATE, "s", ruling(null, null)).statusCode())
        }
        assertEquals(Triple(3, "b", null), admin.ruled)
    }

    private fun ruling(black: String?, white: String?): String {
        fun json(value: String?) = value?.let { "\"$it\"" } ?: "null"
        return """{"session":3,"blackDiscordId":"b","blackAward":${json(black)},"whiteAward":${json(white)}}"""
    }

    private fun authenticated(roles: Set<String>) = SessionResolution.Authenticated(
        DiscordSession("admin", "Admin", "avatar", Date(System.currentTimeMillis() + 60_000), roles)
    )

    private fun withApi(
        resolution: SessionResolution,
        administrator: LeagueAdministrator = RecordingAdministrator(),
        assertion: (Int) -> Unit,
    ) {
        val api = Api(
            sessionResolver = SessionResolver { resolution },
            logReader = LogReader { emptyList() },
            adminRoleIds = { adminRoles },
            leagueAdministrator = administrator,
        )
        val app = Javalin.create()
            .post(REMOVE, api::removeLeagueMember)
            .post(ADJUDICATE, api::adjudicateLeagueMatch)
            .start(0)
        try {
            assertion(app.port())
        } finally {
            app.stop()
        }
    }

    private fun post(port: Int, path: String, goldId: String?, body: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        goldId?.let { builder.header("X-Gold-Id", it) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private companion object {
        const val REMOVE = "/gold/api/admin/league/remove"
        const val ADJUDICATE = "/gold/api/admin/league/adjudicate"
        const val REMOVE_BODY = """{"discordId":"42"}"""
    }
}
