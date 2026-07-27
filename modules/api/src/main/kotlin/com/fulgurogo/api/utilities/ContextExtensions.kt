package com.fulgurogo.api.utilities

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

/**
 * 60 requests per minute, counted per (IP, method, matched route).
 *
 * Signals by **throwing** `TooManyRequestsResponse`, which Javalin maps to 429 — so never call this from inside a
 * catch-all, or the 429 silently becomes a 500. `Api.handle` gets this right.
 */
fun Context.rateLimit() {
    NaiveRateLimit.requestPerTimeUnit(this, 60, TimeUnit.MINUTES)
}
