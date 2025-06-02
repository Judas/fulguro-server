package com.fulgurogo.common.logger

import org.slf4j.LoggerFactory

object Logger {
    fun log(tag: String, msg: String, error: Throwable?) {
        val logger = LoggerFactory.getLogger(tag)
        val message = "[$tag] $msg"
        error?.let { logger.error(message, it) } ?: logger.debug(message)
    }
}

fun Any.log(service: String, message: String, error: Throwable? = null) =
    Logger.log(service, message, error)
