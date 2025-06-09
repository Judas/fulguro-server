package com.fulgurogo.api.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiGoldTier(
    val rank: Int,
    val name: String,
    val min: Int,
    val max: Int
)
