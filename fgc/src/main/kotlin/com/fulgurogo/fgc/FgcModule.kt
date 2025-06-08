package com.fulgurogo.fgc

/**
 * This module is in charge of verifying the validity of players for FGC tournaments.
 */
object FgcModule {
    const val TAG = "FGC"

    private val fgcService = FgcService()

    fun init() {
        fgcService.start()
    }
}
