package com.fulgurogo.features.ladder.api

import com.fulgurogo.features.database.DatabaseAccessor
import io.javalin.http.Context
import io.javalin.http.util.NaiveRateLimit
import java.util.concurrent.TimeUnit

object FulguroApi {
    private fun rateLimit(ctx: Context) {
        NaiveRateLimit.requestPerTimeUnit(ctx, 30, TimeUnit.MINUTES)
    }

    fun getPlayers(ctx: Context) {
        rateLimit(ctx)
        ctx.json(DatabaseAccessor.apiLadderPlayers())
    }
}
