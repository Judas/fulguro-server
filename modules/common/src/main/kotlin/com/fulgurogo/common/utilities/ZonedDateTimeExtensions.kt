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

/**
 * The same instant as a [Date].
 *
 * Do not go back to building a [Calendar] and copying the local fields across. `Calendar.getInstance(Locale.FRANCE)`
 * sets the *locale*, not the zone, so the fields were reinterpreted in the JVM default zone: on a UTC server that
 * shifted every result by the Paris offset (+2h in summer). It also never set `MILLISECOND`, so each result carried
 * whatever millisecond `getInstance()` happened to see, making the conversion non-deterministic.
 */
fun ZonedDateTime.toDate(): Date = Date.from(toInstant())

/** The same instant as a [ZonedDateTime] in [DATE_ZONE], the counterpart of [toDate]. */
fun Date.toZonedDateTime(): ZonedDateTime = toInstant().atZone(DATE_ZONE)

fun ZonedDateTime.toStartOfMonth(): ZonedDateTime = this.withDayOfMonth(1).toStartOfDay()

fun ZonedDateTime.toStartOfDay(): ZonedDateTime = this
    .withHour(0)
    .withMinute(0)
    .withSecond(0)
    .withNano(0)
