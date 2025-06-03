package com.fulgurogo.kgs.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class FfgUserInfo(
    val discordId: String,
    val ffgId: String? = null,
    val ffgName: String? = null,
    val ffgRank: String? = null,
    val updated: Date? = null,
    val error: Date? = null
)
