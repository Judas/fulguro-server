package com.fulgurogo.houses

/**
 * This module is in charge of aggregating points for the houses system.
 */
object HousesModule {
    const val TAG = "HSS"

    private val housesService = HousesService()

    fun init() {
        housesService.start()
    }
}
