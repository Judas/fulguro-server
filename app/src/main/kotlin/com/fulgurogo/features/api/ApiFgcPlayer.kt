package com.fulgurogo.features.api

import com.fulgurogo.features.fgc.FgcPlayer
import kotlin.math.abs
import kotlin.math.roundToInt

data class ApiFgcPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val goldRating: Int,
    val kgsRating: String?,
    val kgsDiff: String,
    val kgsDiffStatus: Int?,
    val ogsRating: String?,
    val ogsDiff: String,
    val ogsDiffStatus: Int?
) {
    companion object {
        fun from(fgcPlayer: FgcPlayer): ApiFgcPlayer {
            val goldRating = fgcPlayer.rating.roundToInt()

            val kgsRating = fgcPlayer.kgsRating()?.roundToInt()
            val kgsRatingString = kgsRating?.let { "$it (${fgcPlayer.kgsRank})" }
            val kgsDiff = kgsRating?.let { goldRating - it }

            val ogsRating = fgcPlayer.ogsRating()?.roundToInt()
            val ogsRatingString = ogsRating?.let { "$it (${fgcPlayer.ogsRank})" }
            val ogsDiff = ogsRating?.let { goldRating - it }

            return ApiFgcPlayer(
                fgcPlayer.discordId,
                fgcPlayer.name ?: "",
                fgcPlayer.avatar ?: "",
                goldRating,
                kgsRatingString,
                kgsDiff.toDiffString(),
                kgsDiff.toDiffStatus(),
                ogsRatingString,
                ogsDiff.toDiffString(),
                ogsDiff.toDiffStatus()
            )
        }
    }
}

fun Int?.toDiffString(): String = when {
    this == null -> ""
    this > 0 -> "+$this"
    else -> "$this"
}

fun Int?.toDiffStatus(): Int = when {
    this == null -> -1
    abs(this) < 200 -> 0
    abs(this) < 300 -> 1
    else -> 2
}