package com.fulgurogo.egf

/**
 * This module fetches the EGF user profile by scraping the EGF website.
 */
object EgfModule {
    const val TAG = "EGF"

    private val egfService = EgfService()

    fun init() {
        // User info service
        egfService.start()
    }
}
