package com.fulgurogo.fox

/**
 * This module fetches the FOX user profile and games by using the unofficial foxwq REST API.
 */
object FoxModule {
    const val TAG = "FOX"

    private val foxService = FoxService()

    fun init() {
        // User info service
        foxService.start()
    }
}
