package com.fulgurogo.fgc.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

@GenerateNoArgConstructor
data class FgcValidityGame(
    val goldId: String,
    val blackDiscordId: String? = null,
    val whiteDiscordId: String? = null,
    val ranked: Boolean
)
