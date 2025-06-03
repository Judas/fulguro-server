package com.fulgurogo.kgs

object FfgModule {
    const val TAG = "FFG"

    private val ffgService = FfgService()

    fun init() {
        // User info service
        ffgService.start()
    }
}
