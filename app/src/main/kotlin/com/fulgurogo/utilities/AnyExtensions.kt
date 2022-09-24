package com.fulgurogo.utilities

fun Any.log(level: Logger.Level, message: String, error: Throwable? = null) =
    Logger.log(level, this::class.java.simpleName, message, error)
