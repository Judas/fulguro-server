package com.fulgurogo.common.db

import com.fulgurogo.common.utilities.DATE_ZONE
import org.sql2o.converters.Converter
import java.time.LocalDateTime
import java.util.*

class CustomDateConverter : Converter<Date> {
    override fun convert(value: Any?): Date? = when (value) {
        is LocalDateTime -> Date.from(value.atZone(DATE_ZONE).toInstant())
        is Date -> value
        else -> null
    }

    override fun toDatabaseParam(value: Date?): Any? = value
        ?.toInstant()
        ?.atZone(DATE_ZONE)
        ?.toLocalDateTime()
}
