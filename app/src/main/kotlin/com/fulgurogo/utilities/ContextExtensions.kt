package com.fulgurogo.utilities

import io.javalin.http.Context
import io.javalin.http.util.NaiveRateLimit
import java.util.concurrent.TimeUnit

fun Context.standardResponse(data: Any) {
    status(200)
    json(data)
}

fun Context.rateLimit() {
    NaiveRateLimit.requestPerTimeUnit(this, 30, TimeUnit.MINUTES)
}
