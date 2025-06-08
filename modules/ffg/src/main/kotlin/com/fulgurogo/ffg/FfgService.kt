package com.fulgurogo.ffg

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.ffg.FfgModule.TAG
import com.fulgurogo.ffg.db.FfgDatabaseAccessor
import com.fulgurogo.ffg.db.model.FfgUserInfo
import org.jsoup.Jsoup
import java.util.*

class FfgService : PeriodicFlowService(0, 60) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        FfgDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Scrap profile page
                val route = "${Config.get("ffg.website.url")}/php/affichePersonne.php?id=${stale.ffgId}"
                val html = Jsoup.connect(route)
                    .userAgent(Config.get("user.agent"))
                    .timeout(Config.get("global.read.timeout.ms").toInt())
                    .get()

                // Get name
                val name = html.select("#ffg_main_content > h3").asList().firstOrNull()?.text()?.trim()
                if (name == "Aucune information disponible") {
                    // Private user or wrong id
                    FfgDatabaseAccessor.markAsError(stale)
                } else {
                    // Get rank
                    val rank = html.select("#ffg_main_content > div").asList()
                        .map { it.text().trim() }
                        .firstOrNull { it.startsWith("Échelle principale : ") }
                        ?.substring(21)
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
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                FfgDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
