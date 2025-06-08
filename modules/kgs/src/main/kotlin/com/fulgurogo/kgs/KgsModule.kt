package com.fulgurogo.kgs

/**
 * This module fetches the KGS user profile and games by scraping the KGS archives.
 */
object KgsModule {
    const val TAG = "KGS"

    private val kgsService = KgsService()

    fun init() {
        // User info service
        kgsService.start()
    }
}
