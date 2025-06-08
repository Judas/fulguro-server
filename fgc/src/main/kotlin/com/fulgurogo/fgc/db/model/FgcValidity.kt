package com.fulgurogo.fgc.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class FgcValidity(
    val discordId: String,
    val totalGames: Int,
    val totalRankedGames: Int,
    val goldGames: Int,
    val goldRankedGames: Int,
    val updated: Date? = null,
    val error: Boolean = false
)
