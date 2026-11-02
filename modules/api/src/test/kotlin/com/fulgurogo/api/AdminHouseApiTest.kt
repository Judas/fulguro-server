package com.fulgurogo.api

import com.fulgurogo.api.admin.HouseAdjustmentResult
import com.fulgurogo.api.admin.HouseAdministrator
import com.fulgurogo.api.admin.LogReader
import com.fulgurogo.api.auth.DiscordSession
import com.fulgurogo.api.auth.SessionResolution
import com.fulgurogo.api.auth.SessionResolver
import com.fulgurogo.api.db.model.ApiHouseAdjustment
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

class AdminHouseApiTest {
    private val adminRoles = setOf("admin-role")

    /** Records what reached the service, so a test can assert that a rejected request never did. */
    private class RecordingAdministrator(
        private val answer: (ApiHouseAdjustment) -> HouseAdjustmentResult = { HouseAdjustmentResult.Done(it) },
    ) : HouseAdministrator {
        var adjusted: Triple<String, Int, String>? = null
        var adminId: String? = null

        override fun adjust(
            discordId: String,
            points: Int,
            reason: String,
            adminDiscordId: String
        ): HouseAdjustmentResult {
            adjusted = Triple(discordId, points, reason)
            adminId = adminDiscordId
            return answer(ApiHouseAdjustment(discordId, "SLUG", "2026-2027", points, reason, 40 + points))
        }
    }

    @Test
    fun `adjusting needs a session and the role`() {
        withApi(SessionResolution.Unauthorized) { port -> assertEquals(401, post(port, null, body()).statusCode()) }
        withApi(SessionResolution.Unavailable) { port -> assertEquals(503, post(port, "s", body()).statusCode()) }
        withApi(authenticated(setOf("other"))) { port -> assertEquals(403, post(port, "s", body()).statusCode()) }
    }

    @Test
    fun `malformed adjustments are bad requests and never reach the houses`() {
        val bodies = listOf(
            "{}",
            """{"points":5,"reason":"r"}""",  // no player
            """{"discordId":" ","points":5,"reason":"r"}""",
            """{"discordId":"42","reason":"r"}""",  // no amount
            body(points = "0"),
            body(points = "101"),
            body(points = "-101"),
            body(points = "5.5"),
            body(points = "\"abc\""),
            """{"discordId":"42","points":5}""",  // no reason
            body(reason = "   "),
            body(reason = "x".repeat(201)),
        )
        for (body in bodies) {
            val admin = RecordingAdministrator()
            withApi(authenticated(adminRoles), admin) { port ->
                assertEquals(400, post(port, "s", body).statusCode(), body)
            }
            assertNull(admin.adjusted, body)
        }
    }

    @Test
    fun `a player in no house is not found`() {
        withApi(authenticated(adminRoles), RecordingAdministrator { HouseAdjustmentResult.NotMember }) { port ->
            assertEquals(404, post(port, "s", body()).statusCode())
        }
    }

    @Test
    fun `the summer break is a conflict`() {
        withApi(authenticated(adminRoles), RecordingAdministrator { HouseAdjustmentResult.OutOfSeason }) { port ->
            assertEquals(409, post(port, "s", body()).statusCode())
        }
    }

    @Test
    fun `admin gives and takes points with a trimmed reason`() {
        for (points in listOf(1, 5, 100, -1, -100)) {
            val admin = RecordingAdministrator()
            withApi(authenticated(adminRoles), admin) { port ->
                val response = post(port, "s", body(points = points.toString(), reason = "  Tournoi d'automne "))
                assertEquals(200, response.statusCode())
                assertTrue(response.body().contains(""""points":$points"""), response.body())
                assertTrue(response.body().contains(""""total":${40 + points}"""), response.body())
            }
            assertEquals(Triple("42", points, "Tournoi d'automne"), admin.adjusted)
            assertEquals("admin", admin.adminId)
        }
    }

    private fun body(points: String = "5", reason: String = "Tournoi"): String =
        """{"discordId":"42","points":$points,"reason":"$reason"}"""

    private fun authenticated(roles: Set<String>) = SessionResolution.Authenticated(
        DiscordSession("admin", "Admin", "avatar", Date(System.currentTimeMillis() + 60_000), roles)
    )

    private fun withApi(
        resolution: SessionResolution,
        administrator: HouseAdministrator = RecordingAdministrator(),
        assertion: (Int) -> Unit,
    ) {
        val api = Api(
            sessionResolver = SessionResolver { resolution },
            logReader = LogReader { emptyList() },
            adminRoleIds = { adminRoles },
            houseAdministrator = administrator,
        )
        val app = Javalin.create().post(PATH, api::adjustHousePoints).start(0)
        try {
            assertion(app.port())
        } finally {
            app.stop()
        }
    }

    private fun post(port: Int, goldId: String?, body: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$PATH"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        goldId?.let { builder.header("X-Gold-Id", it) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private companion object {
        const val PATH = "/gold/api/admin/house/points"
    }
}
