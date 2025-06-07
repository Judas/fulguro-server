package com.fulgurogo.fox.api.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class FoxApiPlayerRating(
    val id: Int,
    val nick: String,
    val rank: String
)
