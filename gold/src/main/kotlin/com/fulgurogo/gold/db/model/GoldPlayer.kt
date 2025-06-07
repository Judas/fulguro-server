package com.fulgurogo.gold.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class GoldPlayer(
    val discordId: String,
    val rating: Double,
    val tierRank: Int,
    val updated: Date? = null,
    val error: Date? = null
)
