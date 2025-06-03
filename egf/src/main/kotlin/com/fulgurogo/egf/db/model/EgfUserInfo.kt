package com.fulgurogo.egf.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class EgfUserInfo(
    val discordId: String,
    val egfId: String? = null,
    val egfName: String? = null,
    val egfRank: String? = null,
    val updated: Date? = null,
    val error: Date? = null
)
