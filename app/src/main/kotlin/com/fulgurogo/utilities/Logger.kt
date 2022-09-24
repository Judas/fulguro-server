package com.fulgurogo.utilities

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
