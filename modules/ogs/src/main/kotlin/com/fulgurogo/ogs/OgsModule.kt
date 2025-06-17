package com.fulgurogo.ogs

/**
 * This module fetches the OGS user profile and games from the REST API.
 * It also connects to the OGS web socket to get real time game informations;
 */
object OgsModule {
    const val TAG = "OGS"
    const val TAG_RT = "ORT"

    private val ogsService = OgsService()
    private val ogsRealTimeService = OgsRealTimeService()

    fun init() {
        // User info & games service
        ogsService.start()

        // Real time game service
        ogsRealTimeService.start()
    }
}
