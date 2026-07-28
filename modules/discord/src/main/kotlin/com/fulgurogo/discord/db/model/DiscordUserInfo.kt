package com.fulgurogo.discord.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class DiscordUserInfo(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val updated: Date? = null,
    val error: Boolean = false,
    /**
     * When the bot first got an authoritative answer that this user is no longer a member of the guild, or null while
     * they are still there. This is the only input to the clean module's user purge — a departure must be recorded
     * explicitly, never inferred from a missing or fallback profile.
     */
    val leftServerSince: Date? = null
)
