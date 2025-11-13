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

fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
    .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
    .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
    .build()

private val scraperCookies: MutableMap<String, String> = mutableMapOf()

fun scrap(url: String): Document {
    val response: Connection.Response = Jsoup.connect(url)
        .header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
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

    scraperCookies.putAll(response.cookies())

    return response.parse()
}
