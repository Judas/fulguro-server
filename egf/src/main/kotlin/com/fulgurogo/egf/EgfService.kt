package com.fulgurogo.egf

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.egf.EgfModule.TAG
import com.fulgurogo.egf.db.EgfDatabaseAccessor
import com.fulgurogo.egf.db.model.EgfUserInfo
import org.jsoup.Jsoup
import java.util.*

class EgfService : PeriodicFlowService(0, 60) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        EgfDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Scrap profile page
                val route = "${Config.get("egf.website.url")}?key=${stale.egfId}"
                val html = Jsoup.connect(route)
                    .userAgent(Config.get("user.agent"))
                    .timeout(Config.get("global.read.timeout.ms").toInt())
                    .get()

                // Get name
                val name = html.select("span.plain5").asList().firstOrNull()?.text()?.trim()
                if (name.isNullOrBlank()) {
                    // Private user or wrong id
                    EgfDatabaseAccessor.markAsError(stale)
                } else {
                    // Get rank
                    val rank = html.select("input").asList()
                        .firstOrNull { it.attr("name") == "grade" }
                        ?.attr("value")

                    EgfDatabaseAccessor.updateUser(
                        EgfUserInfo(
                            discordId = stale.discordId,
                            egfId = stale.egfId,
                            egfName = name,
                            egfRank = if (rank.isNullOrBlank()) "?" else rank,
                            updated = Date(),
                            error = null
                        )
                    )
                }
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                EgfDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }
}
