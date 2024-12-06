package com.fulgurogo.common.logger

import org.slf4j.LoggerFactory

object Logger {
    enum class Level {
        DEBUG, INFO, WARNING, ERROR
    }

    fun log(level: Level, tag: String, msg: String, error: Throwable?) {
        val logger = LoggerFactory.getLogger(tag)
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.WARNING -> logger.warn(msg)
            Level.ERROR -> error?.let { logger.error(msg, it) } ?: logger.error(msg)
        }
    }
}

fun Any.log(level: Logger.Level, message: String, error: Throwable? = null) =
    Logger.log(level, this::class.java.simpleName, message, error)
