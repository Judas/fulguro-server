package com.fulgurogo.api

import com.fulgurogo.api.admin.LogReader
import com.fulgurogo.api.auth.DiscordSession
import com.fulgurogo.api.auth.SessionResolution
import com.fulgurogo.api.auth.SessionResolver
import io.javalin.Javalin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminLogsApiTest {
    private val adminRoles = setOf("admin-role")

    @Test
    fun `missing session is unauthorized`() {
        withApi(SessionResolution.Unauthorized, listOf("secret")) { port ->
            assertEquals(401, request(port).statusCode())
        }
    }

    @Test
    fun `authenticated non admin is forbidden`() {
        withApi(authenticated(setOf("other")), listOf("secret")) { port ->
            assertEquals(403, request(port, "session").statusCode())
        }
    }

    @Test
    fun `discord outage is unavailable`() {
        withApi(SessionResolution.Unavailable, listOf("secret")) { port ->
            assertEquals(503, request(port, "session").statusCode())
        }
    }

    @Test
    fun `missing log is unavailable`() {
        withApi(authenticated(adminRoles), null) { port ->
            assertEquals(503, request(port, "session").statusCode())
        }
    }

    @Test
    fun `admin receives logs and cannot choose another path`() {
        withApi(authenticated(adminRoles), listOf("first", "second")) { port ->
            val response = request(port, "session", "?path=C:/other.log")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"first\""))
            assertTrue(response.body().contains("\"second\""))
            assertTrue(response.body().contains("\"generatedAt\""))
        }
    }

    private fun authenticated(roles: Set<String>) = SessionResolution.Authenticated(
        DiscordSession("1", "Admin", "avatar", Date(System.currentTimeMillis() + 60_000), roles)
    )

    private fun withApi(resolution: SessionResolution, lines: List<String>?, assertion: (Int) -> Unit) {
        val resolver = SessionResolver { resolution }
        val reader = LogReader { lines }
        val api = Api(resolver, reader) { adminRoles }
        val app = Javalin.create().get("/gold/api/admin/logs", api::getAdminLogs).start(0)
        try {
            assertion(app.port())
        } finally {
            app.stop()
        }
    }

    private fun request(port: Int, goldId: String? = null, suffix: String = ""): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port/gold/api/admin/logs$suffix")).GET()
        goldId?.let { builder.header("X-Gold-Id", it) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
