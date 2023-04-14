package com.fulgurogo.features.ladder

data class Tier(
    val id: Int,
    val name: String,
    val min: Int,
    val max: Int,
    val bgColor: String,
    val fgColor: String
)
