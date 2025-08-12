package com.fulgurogo.houses.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import java.util.*

@GenerateNoArgConstructor
data class HouseUserInfo(
    val discordId: String,
    val houseId: Int,
    val played: Int,
    val gold: Int,
    val house: Int,
    val win: Int,
    val long: Int,
    val balanced: Int,
    val ranked: Int,
    val fgc: Int,
    val checkedDate: Date? = null,
    val updated: Date? = null,
    val error: Boolean = false
)
