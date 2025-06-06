package com.fulgurogo.ogs

object OgsModule {
    const val TAG = "OGS"

    private val ogsService = OgsService()
    private val ogsRealTimeService = OgsRealTimeService()

    fun init() {
        // User info & games service
        ogsService.start()

        // Real time game service
        ogsRealTimeService.start()
    }
}
