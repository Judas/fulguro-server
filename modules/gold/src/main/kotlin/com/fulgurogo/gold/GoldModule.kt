package com.fulgurogo.gold

/**
 * This module is in charge of assigning a Gold tier to the players.
 */
object GoldModule {
    const val TAG = "GLD"

    private val goldService = GoldService()

    fun init() {
        goldService.start()
    }
}
