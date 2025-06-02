package com.fulgurogo.features.fgc

import com.fulgurogo.utilities.NoArg
import com.fulgurogo.utilities.toRating

@NoArg
data class FgcPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val rating: Double,
    val kgsRank: String? = null,
    val ogsRank: String? = null,
    val ffgRank: String? = null
) {
    fun kgsRating(): Double? = when {
        kgsRank.isNullOrBlank() -> null
        kgsRank.contains("?") -> null
        else -> kgsRank.toRating()
    }

    fun ogsRating(): Double? = if (ogsRank.isNullOrBlank()) null else ogsRank.toRating()

    fun ffgRating(): Double? = if (ffgRank.isNullOrBlank()) null else ffgRank.toRating()
}
