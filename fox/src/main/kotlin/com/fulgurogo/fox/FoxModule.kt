package com.fulgurogo.fox

object FoxModule {
    const val TAG = "FOX"

    private val foxService = FoxService()

    fun init() {
        // User info service
        foxService.start()
    }
}
