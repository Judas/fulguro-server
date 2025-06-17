package com.fulgurogo.ping

/**
 * This module is in charge of pinging the fronted to avoid downtime.
 */
object PingModule {
    const val TAG = "PNG"

    private val pingService = PingService()

    fun init() {
        pingService.start()
    }
}
