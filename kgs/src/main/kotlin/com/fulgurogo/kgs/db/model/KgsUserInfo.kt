package com.fulgurogo.kgs.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class KgsUserInfo(
    val discordId: String,
    val kgsId: String? = null,
    val kgsRank: String? = null,
    val updated: Date? = null,
    val error: Date? = null
)
