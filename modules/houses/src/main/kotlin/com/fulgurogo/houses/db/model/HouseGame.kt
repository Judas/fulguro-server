package com.fulgurogo.houses.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class HouseGame(
    val goldId: String,
    val date: Date,
    val result: String,
    val longGame: String,
    val handicap: String,
    val komi: String,
    val ranked: String,
    val blackDiscordId: String? = null,
    val blackHouseId: Int? = null,
    val whiteDiscordId: String? = null,
    val whiteHouseId: Int? = null
)
