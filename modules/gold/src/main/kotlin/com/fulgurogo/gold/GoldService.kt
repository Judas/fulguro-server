package com.fulgurogo.gold

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.service.PeriodicFlowService
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.discord.db.model.DiscordUserInfo
import com.fulgurogo.gold.GoldModule.TAG
import com.fulgurogo.gold.db.GoldDatabaseAccessor
import com.fulgurogo.gold.db.model.GoldPlayer
import java.util.*

class GoldService : PeriodicFlowService(60, 5) {
    private var processing = false

    override fun onTick() {
        if (processing) return
        processing = true

        // Get stalest user
        GoldDatabaseAccessor.stalestUser()?.let { stale ->
            try {
                // Get user ranks
                val ranks = GoldDatabaseAccessor.userRanks(stale)
                if (ranks == null) {
                    GoldDatabaseAccessor.markAsError(stale)
                    return@let
                }

                // Calculate player rating
                val rating = ranks.computeRating()
                if (rating == null) {
                    GoldDatabaseAccessor.markAsError(stale)
                    return@let
                }

                // Get corresponding tier rank
                val tier = GoldDatabaseAccessor.tierFor(rating)

                GoldDatabaseAccessor.updatePlayer(
                    GoldPlayer(
                        discordId = stale.discordId,
                        rating = rating,
                        tierRank = tier.rank,
                        updated = Date(),
                        error = false
                    )
                )

                if (stale.tierRank < tier.rank && stale.tierRank != 0) {
                    DiscordDatabaseAccessor.user(stale.discordId)?.let {
                        notifyRankUpdate(it, tier.name)
                    }
                }
            } catch (e: Exception) {
                log(TAG, "onTick FAILURE ${e.message}")
                GoldDatabaseAccessor.markAsError(stale)
            }
        }
        processing = false
    }

    private fun notifyRankUpdate(discordUserInfo: DiscordUserInfo, tierName: String) {
        val title = ":tada: Promotion Gold"
        DiscordModule.discordBot.sendMessageEmbeds(
            channelId = Config.get("bot.notification.channel.id"),
            message = "**${discordUserInfo.discordName}** est désormais **$tierName** !",
            title = title,
            imageUrl = discordUserInfo.discordAvatar ?: ""
        )
    }
}
