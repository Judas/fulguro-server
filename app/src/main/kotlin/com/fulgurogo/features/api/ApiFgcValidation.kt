package com.fulgurogo.features.api

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class ApiFgcValidation(
    val gameCount: Int = 0,
    val ladderGameCount: Int = 0,
    val period: Int = 30,
)
