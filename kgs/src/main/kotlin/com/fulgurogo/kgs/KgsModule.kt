package com.fulgurogo.kgs

object KgsModule {
    const val TAG = "KGS"

    private val kgsService = KgsService()

    fun init() {
        // User info service
        kgsService.start()
    }
}
