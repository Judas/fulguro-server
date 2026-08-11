package com.fulgurogo.common.utilities

import com.fulgurogo.common.config.Config
import okhttp3.CookieJar
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * The shared outbound HTTP client.
 *
 * One instance, deliberately. Every OkHttpClient carries its own connection pool and dispatcher thread pool, and this
 * used to be a factory: `KgsService.fetchSgf` called it once per SGF download, so a single tick over a busy player
 * built dozens of clients. None were ever shut down, and no request ever reused a connection.
 */
val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .build()
}

/**
 * The same client with **no cookie jar**, for APIs that authenticate on headers alone.
 *
 * It exists because the shared jar is not neutral. `OgsRealTimeService` logs into OGS with a real account to open its
 * WebSocket, and that login's `sessionid` lands in [okHttpClient]'s jar, where it is then replayed on **every** later
 * request to online-go.com. Django Rest Framework sees a session-authenticated request, applies its CSRF check, and
 * answers `403 CSRF Failed: Referer checking failed - no Referer.` — so the OGS league's writes, which authenticate on
 * `X-OGS-LEAGUE*` headers and want nothing to do with that session, failed for a reason nothing in their own code
 * suggested. Measured: the same `PUT` answers 200 before the login and 403 after it.
 *
 * Sending a `Referer` would have silenced the check, but the right fix is not to send the cookie: an organiser endpoint
 * has no business carrying a user's session, and doing so risks OGS attributing the call to that account.
 *
 * Built with [OkHttpClient.newBuilder], so the connection pool and the dispatcher are **shared** with [okHttpClient] —
 * this is one more configuration, not one more thread pool, which is the mistake the comment above records.
 */
val okHttpClientWithoutCookies: OkHttpClient by lazy {
    okHttpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
}

/**
 * Fetches [url] as a browser would, sending [cookies] and returning the response so its own cookies can be kept.
 *
 * Jsoup carries no session state of its own — every connection starts empty, and the [okHttpClient] cookie jar is a
 * different stack entirely — so anything behind a login has to thread its cookies through here by hand. `KgsSession`
 * is the one caller that does; [scrap] with no cookies is the stateless call for everything else.
 */
fun scrapResponse(url: String, cookies: Map<String, String> = mapOf()): Connection.Response =
    browserConnection(url)
        .cookies(cookies)
        .method(Connection.Method.GET)
        .execute()

fun scrap(url: String, cookies: Map<String, String> = mapOf()): Document = scrapResponse(url, cookies).parse()

/** Posts [data] as a form, for the login forms that stand in front of a scraped page. */
fun postForm(url: String, data: Map<String, String>, cookies: Map<String, String> = mapOf()): Connection.Response =
    browserConnection(url)
        .cookies(cookies)
        .data(data)
        .method(Connection.Method.POST)
        .execute()

private fun browserConnection(url: String): Connection {
    return Jsoup.connect(url)
        .header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        // English, and it matters. gokgs.com honours this header, and asking for French got us French archive pages:
        // dates as "28/07/26 05:39" (no AM/PM, so the parse in KgsService returned null and every game row was
        // silently dropped) and, worse, results as "B+" for *Blanc* -- white -- which the parser reads as black. The
        // header used to be French-first for the FFG and EGF sites, both removed in 8.8; the only scraped site left
        // is KGS, which is parsed in English.
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Accept-Encoding", "gzip, deflate, br")
        .header("Upgrade-Insecure-Requests", "1")
        .header("Sec-Fetch-dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "cross-site")
        .header("Sec-Fetch-User", "?1")
        .header("Sec-Ch-Ua-Platform", "macOS")
        .header("Sec-Ch-Ua-Mobile", "?0")
        .header("Sec-Ch-Ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"")
        .header("Cache-Control", "max-age=0")
        .followRedirects(true)
        .userAgent(Config.get("user.agent"))
        .referrer("https://www.google.com")
        .timeout(Config.get("global.read.timeout.ms").toInt())
}
