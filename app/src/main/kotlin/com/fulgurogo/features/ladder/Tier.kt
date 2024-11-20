package com.fulgurogo.features.ladder

import com.fulgurogo.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class Tier(
    val rank: Int,
    val name: String,
    val min: Int,
    val max: Int
)
