package com.fulgurogo.igs

object IgsModule {
    const val TAG = "IGS"

    private val igsService = IgsService()

    fun init() {
        // User info service
        igsService.start()
    }
}
