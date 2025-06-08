package com.fulgurogo.ogs.api.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class OgsApiPlayerRating(
    val id: Long,
    val username: String,
    val ranking: Double
)
