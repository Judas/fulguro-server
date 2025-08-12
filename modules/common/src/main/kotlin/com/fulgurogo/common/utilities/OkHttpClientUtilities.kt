package com.fulgurogo.common.utilities

import com.fulgurogo.common.config.Config
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
    .readTimeout(Config.get("global.read.timeout.ms").toLong(), TimeUnit.MILLISECONDS)
    .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
    .build()
