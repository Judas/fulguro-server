package com.fulgurogo.fox.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class FoxUserInfo(
    val discordId: String,
    val foxId: Int = 0,
    val foxName: String? = null,
    val foxRank: String? = null,
    val updated: Date? = null,
    val error: Boolean = false
)
