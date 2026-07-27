package com.fulgurogo.gold

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.service.StalestFirstService
import com.fulgurogo.discord.DiscordModule
import com.fulgurogo.discord.db.DiscordDatabaseAccessor
import com.fulgurogo.discord.db.model.DiscordUserInfo
import com.fulgurogo.gold.GoldModule.TAG
import com.fulgurogo.gold.db.GoldDatabaseAccessor
import com.fulgurogo.gold.db.model.GoldPlayer
import java.util.*

class GoldService : StalestFirstService<GoldPlayer>(60, 15, TAG) {
    override fun stalest(): GoldPlayer? = GoldDatabaseAccessor.stalestUser()

    override fun markAsError(stale: GoldPlayer) = GoldDatabaseAccessor.markAsError(stale)

    override fun refresh(stale: GoldPlayer) {
        // Get user ranks
        val ranks = GoldDatabaseAccessor.userRanks(stale)
        if (ranks == null) {
            markAsError(stale)
            return
        }

        // Calculate player rating
        val rating = ranks.computeRating()
        if (rating == null) {
            markAsError(stale)
            return
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
