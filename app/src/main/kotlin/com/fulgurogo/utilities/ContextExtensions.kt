package com.fulgurogo.utilities

import io.javalin.http.Context
import io.javalin.http.util.NaiveRateLimit
import java.util.concurrent.TimeUnit

fun Context.standardResponse(data: Any) {
    status(200)
    json(data)
}

fun Context.standardResponse() {
    status(204)
}

fun Context.notFoundError() {
    status(404)
}

fun Context.internalError() {
    status(500)
}

fun Context.rateLimit() {
    NaiveRateLimit.requestPerTimeUnit(this, 60, TimeUnit.MINUTES)
}
