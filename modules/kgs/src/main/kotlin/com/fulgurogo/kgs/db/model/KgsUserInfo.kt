package com.fulgurogo.kgs.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class KgsUserInfo(
    val discordId: String,
    val kgsId: String? = null,
    val kgsRank: String? = null,
    /** Date of the archived game [kgsRank] was read from, which is how old that rank is. Null when unknown. */
    val kgsRankDate: Date? = null,
    val updated: Date? = null,
    val error: Boolean = false
)
