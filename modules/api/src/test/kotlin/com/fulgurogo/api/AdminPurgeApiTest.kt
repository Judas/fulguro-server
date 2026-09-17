package com.fulgurogo.api

import com.fulgurogo.api.admin.LogReader
import com.fulgurogo.api.admin.PlayerPurger
import com.fulgurogo.api.auth.DiscordSession
import com.fulgurogo.api.auth.SessionResolution
import com.fulgurogo.api.auth.SessionResolver
import com.fulgurogo.clean.db.model.PlayerPurgeReport
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

class AdminPurgeApiTest {
    private val adminRoles = setOf("admin-role")

    @Test
    fun `missing session is unauthorized`() {
        withApi(SessionResolution.Unauthorized) { port -> assertEquals(401, request(port).statusCode()) }
    }

    @Test
    fun `discord outage is unavailable`() {
        withApi(SessionResolution.Unavailable) { port -> assertEquals(503, request(port, "session").statusCode()) }
    }

    @Test
    fun `non admin is forbidden`() {
        withApi(authenticated(setOf("other"))) { port -> assertEquals(403, request(port, "session").statusCode()) }
    }

    @Test
    fun `empty body and blank id are bad requests`() {
        for (body in listOf("{}", """{"discordId":""}""", """{"discordId":"   "}""")) {
            withApi(authenticated(adminRoles)) { port ->
                assertEquals(400, request(port, "session", body).statusCode())
            }
        }
    }

    /** A bad body must not reach the purger: this route has no undo. */
    @Test
    fun `bad body never purges`() {
        var purged: String? = null
        val purger = PlayerPurger { id ->
            purged = id
            PlayerPurgeReport(id, emptyMap())
        }
        withApi(authenticated(adminRoles), purger) { port ->
            assertEquals(400, request(port, "session", "{}").statusCode())
        }
        assertNull(purged)
    }

    @Test
    fun `admin purges the player named in the body`() {
        var purged: String? = null
        val purger = PlayerPurger { id ->
            purged = id
            PlayerPurgeReport(id, mapOf("house_members" to 1, "house_points" to 12, "discord_user_info" to 1))
        }
        withApi(authenticated(adminRoles), purger) { port ->
            val response = request(port, "session")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains(""""house_points":12"""), response.body())
        }
        assertEquals("1089018734942883850", purged)
    }

    /**
     * The case the route exists for: `CleanService` has already taken the Discord row, so only the points are left.
     * That is a 200 with counts, not a 404 — refusing here would refuse exactly the player who still needs purging.
     */
    @Test
    fun `player already gone from discord still purges their points`() {
        val purger = PlayerPurger { id -> PlayerPurgeReport(id, mapOf("house_points" to 8)) }
        withApi(authenticated(adminRoles), purger) { port ->
            assertEquals(200, request(port, "session").statusCode())
        }
    }

    /** A mistyped id reads as an all-zero report rather than an error. */
    @Test
    fun `unknown player is an empty report`() {
        val purger = PlayerPurger { id -> PlayerPurgeReport(id, mapOf("house_points" to 0)) }
        withApi(authenticated(adminRoles), purger) { port ->
            val response = request(port, "session")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains(""""house_points":0"""), response.body())
        }
    }

    private fun authenticated(roles: Set<String>) = SessionResolution.Authenticated(
        DiscordSession("admin", "Admin", "avatar", Date(System.currentTimeMillis() + 60_000), roles)
    )

    private fun withApi(
        resolution: SessionResolution,
        purger: PlayerPurger = PlayerPurger { PlayerPurgeReport(it, emptyMap()) },
        assertion: (Int) -> Unit,
    ) {
        val api = Api(
            sessionResolver = SessionResolver { resolution },
            logReader = LogReader { emptyList() },
            adminRoleIds = { adminRoles },
            playerPurger = purger,
        )
        val app = Javalin.create().post("/gold/api/admin/purge", api::purgePlayer).start(0)
        try {
            assertion(app.port())
        } finally {
            app.stop()
        }
    }

    private fun request(
        port: Int,
        goldId: String? = null,
        body: String = """{"discordId":"1089018734942883850"}""",
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port/gold/api/admin/purge"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        goldId?.let { builder.header("X-Gold-Id", it) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
