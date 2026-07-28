package com.fulgurogo.common.utilities

import com.fulgurogo.common.config.Config
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

fun scrap(url: String): Document {
    val response: Connection.Response = Jsoup.connect(url)
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
        .header("Connection", "keep-alive")
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
        .method(Connection.Method.GET)
        .execute()

    return response.parse()
}
