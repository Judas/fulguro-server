package com.fulgurogo.common.utilities

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

val DATE_ZONE: ZoneId = ZoneId.of("Europe/Paris")

fun ZonedDateTime.millisecondsFromNow(): Long {
    val now = ZonedDateTime.now(DATE_ZONE)
    return abs(ChronoUnit.MILLIS.between(this, now))
}

fun ZonedDateTime.toDate(): Date = Calendar.getInstance(Locale.FRANCE).let {
    it.set(Calendar.YEAR, year)
    it.set(Calendar.MONTH, monthValue - 1)
    it.set(Calendar.DAY_OF_MONTH, dayOfMonth)
    it.set(Calendar.HOUR_OF_DAY, hour)
    it.set(Calendar.MINUTE, minute)
    it.set(Calendar.SECOND, second)
    it.time
}

fun ZonedDateTime.toStartOfMonth(): ZonedDateTime = this.withDayOfMonth(1).toStartOfDay()

fun ZonedDateTime.toStartOfDay(): ZonedDateTime = this
    .withHour(0)
    .withMinute(0)
    .withSecond(0)
    .withNano(0)
