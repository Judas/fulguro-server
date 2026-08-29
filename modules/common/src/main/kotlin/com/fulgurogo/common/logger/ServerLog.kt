package com.fulgurogo.common.logger

import java.nio.file.Files
import java.nio.file.Path

/** The one log file written by slf4j-simple and exposed by the read-only admin API. */
object ServerLog {
    private const val LOCAL_PATH = "logs/fulgurogo-server.log"
    private const val PRODUCTION_PATH = "/root/logs/fulgurogo-server.log"

    private lateinit var configuredPath: Path

    fun initialize(debug: Boolean) {
        configuredPath = Path.of(if (debug) LOCAL_PATH else PRODUCTION_PATH).toAbsolutePath().normalize()
        configuredPath.parent?.let(Files::createDirectories)
        System.setProperty("org.slf4j.simpleLogger.logFile", configuredPath.toString())
    }

    fun path(): Path {
        check(::configuredPath.isInitialized) { "ServerLog.initialize must run before the API starts" }
        return configuredPath
    }
}
