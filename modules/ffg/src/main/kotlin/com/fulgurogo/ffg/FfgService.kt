package com.fulgurogo.ffg

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.common.utilities.scrap
import com.fulgurogo.ffg.FfgModule.TAG
import com.fulgurogo.ffg.db.FfgDatabaseAccessor
import com.fulgurogo.ffg.db.model.FfgUserInfo
import java.util.*

class FfgService : StalestFirstService<FfgUserInfo>(0, 120, TAG) {
    override fun stalest(): FfgUserInfo? = FfgDatabaseAccessor.stalestUser()

    override fun markAsError(stale: FfgUserInfo) = FfgDatabaseAccessor.markAsError(stale)

    override fun refresh(stale: FfgUserInfo) {
        // Scrap profile page
        val route = "${Config.get("ffg.website.url")}/php/affichePersonne.php?id=${stale.ffgId}"
        val html = scrap(route)

        // Get name
        val name = html.select("#ffg_main_content > h3").asList().firstOrNull()?.text()?.trim()
        if (name == "Aucune information disponible") {
            // Private user or wrong id
            markAsError(stale)
            return
        }

        // Get rank
        val rank = html.select("#ffg_main_content > div").asList()
            .map { it.text().trim() }
            .firstOrNull { it.startsWith("Échelle principale : ") }
            ?.substring(21)
            ?.replace("n/a", "?")
            ?: "?"

        FfgDatabaseAccessor.updateUser(
            FfgUserInfo(
                discordId = stale.discordId,
                ffgId = stale.ffgId,
                ffgName = name,
                ffgRank = rank,
                updated = Date(),
                error = false
            )
        )
    }
}
