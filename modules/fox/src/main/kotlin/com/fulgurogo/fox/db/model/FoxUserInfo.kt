package com.fulgurogo.fox.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.Date

@GenerateNoArgConstructor
data class FoxUserInfo(
    val discordId: String,
    val foxId: String,
    val foxName: String,
    val foxRank: String,
    val totalWin: Int? = null,
    val totalLost: Int? = null,
    val totalEqual: Int? = null,
    val updated: Date? = null,
    val error: Boolean = false,
)
