package com.fulgurogo.igs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.igs.IgsModule.TAG
import com.fulgurogo.igs.db.IgsDatabaseAccessor
import com.fulgurogo.igs.db.model.IgsUserInfo
import java.util.*

class IgsService : PeriodicFlowService(0, 60) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        IgsDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                val telnetClient = IgsTelnetClient()
                // Connect
                telnetClient.connect(Config.get("igs.server.host"), Config.get("igs.server.port").toInt())
                telnetClient.readUntil("Login: ")
                // Login
                telnetClient.write(Config.get("igs.user.name"))
                telnetClient.readUntil("1 1")
                telnetClient.write(Config.get("igs.user.password"))
                telnetClient.readUntil("1 5")
                // Get user profile
                telnetClient.write("stats ${stale.igsId}")
                val playerInfo = telnetClient.readUntil("1 5")
                // Disconnect
                telnetClient.disconnect()

                if (playerInfo.contains("5 Cannot find player.")) {
                    // Private user or wrong id
                    IgsDatabaseAccessor.markAsError(stale)
                } else {
                    // Get rank
                    val rank = playerInfo.split("\n")
                        .firstOrNull { it.startsWith("9 Rating:") }
                        ?.substring(9)?.trim()
                        ?.split(" ")[0]
                        ?.replace("*", "")
                        ?.replace("NR", "?")
                        ?: "?"

                    IgsDatabaseAccessor.updateUser(
                        IgsUserInfo(
                            discordId = stale.discordId,
                            igsId = stale.igsId,
                            igsRank = rank,
                            updated = Date(),
                            error = null
                        )
                    )
                }
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                IgsDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
