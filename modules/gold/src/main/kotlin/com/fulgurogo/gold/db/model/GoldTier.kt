package com.fulgurogo.gold.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class GoldTier(
    val rank: Int,
    val name: String,
    val min: Int,
    val max: Int
)
