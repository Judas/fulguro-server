package com.fulgurogo.ffg

/**
 * This module fetches the FFG user profile by scraping the FFG website.
 */
object FfgModule {
    const val TAG = "FFG"

    private val ffgService = FfgService()

    fun init() {
        // User info service
        ffgService.start()
    }
}
