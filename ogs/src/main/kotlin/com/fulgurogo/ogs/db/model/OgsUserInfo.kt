package com.fulgurogo.ogs.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class OgsUserInfo(
    val discordId: String,
    val ogsId: Int = 0,
    val ogsName: String? = null,
    val ogsRank: String? = null,
    val updated: Date? = null,
    val error: Boolean = false
)
