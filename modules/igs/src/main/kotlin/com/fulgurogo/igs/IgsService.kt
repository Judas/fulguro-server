package com.fulgurogo.igs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.igs.IgsModule.TAG
import com.fulgurogo.igs.db.IgsDatabaseAccessor
import com.fulgurogo.igs.db.model.IgsUserInfo
import java.util.*

class IgsService : StalestFirstService<IgsUserInfo>(0, 60, TAG) {
    override fun stalest(): IgsUserInfo? = IgsDatabaseAccessor.stalestUser()

    override fun markAsError(stale: IgsUserInfo) = IgsDatabaseAccessor.markAsError(stale)

    override fun refresh(stale: IgsUserInfo) {
        val playerInfo = fetchPlayerInfo(stale)

        if (playerInfo.contains("5 Cannot find player.")) {
            // Private user or wrong id
            markAsError(stale)
            return
        }

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
                error = false
            )
        )
    }

    private fun fetchPlayerInfo(stale: IgsUserInfo): String {
        val telnetClient = IgsTelnetClient()
        // finally, because the socket used to leak on every failure path: the disconnect only ran on success
        try {
            // Connect
            telnetClient.connect(
                Config.get("igs.server.host"),
                Config.get("igs.server.port").toInt(),
                Config.get("global.read.timeout.ms").toInt()
            )
            telnetClient.readUntil("Login: ")
            // Login
            telnetClient.write(Config.get("igs.user.name"))
            telnetClient.readUntil("1 1")
            telnetClient.write(Config.get("igs.user.password"))
            telnetClient.readUntil("1 5")
            // Get user profile
            telnetClient.write("stats ${stale.igsId}")
            return telnetClient.readUntil("1 5")
        } finally {
            telnetClient.disconnect()
        }
    }
}
