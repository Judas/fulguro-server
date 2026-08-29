package com.fulgurogo.api

import com.fulgurogo.api.admin.LogReader
import com.fulgurogo.api.auth.DiscordSession
import com.fulgurogo.api.auth.SessionResolution
import com.fulgurogo.api.auth.SessionResolver
import com.fulgurogo.api.link.AccountUnlinkResult
import com.fulgurogo.api.link.AccountUnlinker
import io.javalin.Javalin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminUnlinkApiTest {
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
    fun `invalid body and unknown server are bad requests`() {
        withApi(authenticated(adminRoles)) { port ->
            assertEquals(400, request(port, "session", "{}").statusCode())
        }
        withApi(authenticated(adminRoles), AccountUnlinkResult.UNKNOWN_SERVER) { port ->
            assertEquals(400, request(port, "session").statusCode())
        }
    }

    @Test
    fun `unknown player or stale association is not found`() {
        for (result in listOf(AccountUnlinkResult.UNKNOWN_PLAYER, AccountUnlinkResult.ASSOCIATION_NOT_FOUND)) {
            withApi(authenticated(adminRoles), result) { port ->
                assertEquals(404, request(port, "session").statusCode())
            }
        }
    }

    @Test
    fun `exact association is removed`() {
        var received: Triple<String, String, String>? = null
        val unlinker = AccountUnlinker { discordId, server, accountId ->
            received = Triple(discordId, server, accountId)
            AccountUnlinkResult.REMOVED
        }
        withApi(authenticated(adminRoles), unlinker = unlinker) { port ->
            assertEquals(200, request(port, "session").statusCode())
        }
        assertEquals(Triple("player", "FOX", "account-42"), received)
    }

    private fun authenticated(roles: Set<String>) = SessionResolution.Authenticated(
        DiscordSession("admin", "Admin", "avatar", Date(System.currentTimeMillis() + 60_000), roles)
    )

    private fun withApi(
        resolution: SessionResolution,
        result: AccountUnlinkResult = AccountUnlinkResult.REMOVED,
        unlinker: AccountUnlinker = AccountUnlinker { _, _, _ -> result },
        assertion: (Int) -> Unit,
    ) {
        val api = Api(
            sessionResolver = SessionResolver { resolution },
            logReader = LogReader { emptyList() },
            adminRoleIds = { adminRoles },
            accountUnlinker = unlinker,
        )
        val app = Javalin.create().post("/gold/api/admin/unlink", api::unlinkAccount).start(0)
        try {
            assertion(app.port())
        } finally {
            app.stop()
        }
    }

    private fun request(
        port: Int,
        goldId: String? = null,
        body: String = """{"discordId":"player","account":"FOX","accountId":"account-42"}""",
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port/gold/api/admin/unlink"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        goldId?.let { builder.header("X-Gold-Id", it) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
