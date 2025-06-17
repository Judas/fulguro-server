package com.fulgurogo.clean

/**
 * This module is in charge of cleaning old games from DB, as well as invalid users.
 */
object CleanModule {
    const val TAG = "CLN"

    private val cleanService = CleanService()

    fun init() {
        cleanService.start()
    }
}
