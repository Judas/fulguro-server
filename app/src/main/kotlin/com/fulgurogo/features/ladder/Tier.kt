package com.fulgurogo.features.ladder

import com.fulgurogo.utilities.NoArg

@NoArg
data class Tier(
    val rank: Int,
    val name: String,
    val min: Int,
    val max: Int
)
