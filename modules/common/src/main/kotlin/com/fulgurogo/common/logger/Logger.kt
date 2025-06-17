package com.fulgurogo.common.logger

import org.slf4j.LoggerFactory

fun log(service: String, message: String, error: Throwable? = null) {
    val logger = LoggerFactory.getLogger("[$service]")
    error?.let { logger.error(message, it) } ?: logger.info(message)
}
