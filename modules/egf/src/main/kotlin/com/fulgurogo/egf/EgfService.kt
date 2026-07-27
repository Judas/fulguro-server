package com.fulgurogo.egf

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.common.utilities.scrap
import com.fulgurogo.egf.EgfModule.TAG
import com.fulgurogo.egf.db.EgfDatabaseAccessor
import com.fulgurogo.egf.db.model.EgfUserInfo
import java.util.*

class EgfService : StalestFirstService<EgfUserInfo>(0, 120, TAG) {
    override fun stalest(): EgfUserInfo? = EgfDatabaseAccessor.stalestUser()

    override fun markAsError(stale: EgfUserInfo) = EgfDatabaseAccessor.markAsError(stale)

    override fun refresh(stale: EgfUserInfo) {
        // Scrap profile page
        val route = "${Config.get("egf.website.url")}?key=${stale.egfId}"
        val html = scrap(route)

        // Get name
        val name = html.select("span.plain5").asList().firstOrNull()?.text()?.trim()
        if (name.isNullOrBlank()) {
            // Private user or wrong id
            markAsError(stale)
            return
        }

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
                error = false
            )
        )
    }
}
