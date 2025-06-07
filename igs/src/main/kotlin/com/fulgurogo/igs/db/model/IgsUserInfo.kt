package com.fulgurogo.igs.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class IgsUserInfo(
    val discordId: String,
    val igsId: String? = null,
    val igsRank: String? = null,
    val updated: Date? = null,
    val error: Boolean = false
)
