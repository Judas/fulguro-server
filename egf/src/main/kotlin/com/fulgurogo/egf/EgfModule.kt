package com.fulgurogo.egf

object EgfModule {
    const val TAG = "EGF"

    private val egfService = EgfService()

    fun init() {
        // User info service
        egfService.start()
    }
}
