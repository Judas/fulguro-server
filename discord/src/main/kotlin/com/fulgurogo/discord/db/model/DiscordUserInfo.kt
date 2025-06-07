package com.fulgurogo.discord.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class DiscordUserInfo(
    val discordId: String,
    val discordName: String? = null,
    val discordAvatar: String? = null,
    val updated: Date? = null,
    val error: Boolean = false
)
