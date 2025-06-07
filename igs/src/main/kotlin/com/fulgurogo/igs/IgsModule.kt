package com.fulgurogo.igs

/**
 * This module fetches the IGS user profile by using the telnet client.
 */
object IgsModule {
    const val TAG = "IGS"

    private val igsService = IgsService()

    fun init() {
        // User info service
        igsService.start()
    }
}
