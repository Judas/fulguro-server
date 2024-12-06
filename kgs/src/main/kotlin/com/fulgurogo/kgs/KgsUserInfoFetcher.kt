package com.fulgurogo.kgs

import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.kgs.db.KgsDatabaseAccessor
import com.fulgurogo.kgs.protocol.KgsClient

class KgsUserInfoFetcher : PeriodicFlowService(0, 2) {
    private var processing = false
    private val kgsClient = KgsClient()

    override fun tick() {
        if (processing) return
        processing = true
        log(INFO, "tick")

        // Get stalest user
        val stalestUser = KgsDatabaseAccessor.stalestUser()

        // Fetch its updated info
        stalestUser?.let {
            val updatedUser = kgsClient.getUserInfo(it)

            // Update in DB
            KgsDatabaseAccessor.updateUser(updatedUser)
        }

        processing = false
    }
}
