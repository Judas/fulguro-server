package com.fulgurogo.features.events

import java.util.*

data class Event(
    val type: String,
    val start: Date,
    val end: Date
)