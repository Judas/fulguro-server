package com.fulgurogo.common.utilities

import com.fulgurogo.common.config.Config
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

// Rnadom Free proxies from https://free-proxy-list.net/fr/
private val proxies = listOf(
    "170.130.202.134" to 3128,
    "152.53.168.53" to 44887,
    "65.108.203.37" to 18080,
    "18.135.100.214" to 3128,
    "162.212.153.46" to 8888,
    "209.121.164.50" to 31147,
    "92.67.186.210" to 80,
    "45.146.163.31" to 80,
    "20.27.15.111" to 8561,
    "5.188.183.253" to 8080
)

fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
    .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
    .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
    .build()

fun scrap(url: String): Document {
    val proxy = proxies.random()
    return Jsoup.connect(url)
        .userAgent(Config.get("user.agent"))
        .timeout(Config.get("global.read.timeout.ms").toInt())
        .proxy(proxy.first, proxy.second)
        .get()
}
