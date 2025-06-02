package com.fulgurogo.features.api

import com.fulgurogo.features.fgc.FgcPlayer
import kotlin.math.abs
import kotlin.math.roundToInt

data class ApiFgcPlayer(
    val discordId: String,
    val name: String? = null,
    val avatar: String? = null,
    val goldRating: Int,
    val kgs: RatingComparison?,
    val ogs: RatingComparison?,
    val ffg: RatingComparison?
) {
    companion object {
        fun from(fgcPlayer: FgcPlayer): ApiFgcPlayer {
            val goldRating = fgcPlayer.rating.roundToInt()
            return ApiFgcPlayer(
                fgcPlayer.discordId,
                fgcPlayer.name ?: "",
                fgcPlayer.avatar ?: "",
                goldRating,
                fgcPlayer.kgsRating()?.roundToInt()?.let { RatingComparison.from(goldRating, it, fgcPlayer.kgsRank) },
                fgcPlayer.ogsRating()?.roundToInt()?.let { RatingComparison.from(goldRating, it, fgcPlayer.ogsRank) },
                fgcPlayer.ffgRating()?.roundToInt()?.let { RatingComparison.from(goldRating, it, fgcPlayer.ffgRank) },
            )
        }
    }
}

data class RatingComparison(
    val rating: String?,
    val diff: String,
    val diffStatus: Int?
) {
    companion object {
        fun from(goldRating: Int, serverRating: Int?, serverRank: String?): RatingComparison = RatingComparison(
            serverRating?.let { "$it ($serverRank)" },
            serverRating?.let { it - goldRating }.toDiffString(),
            serverRating?.let { it - goldRating }.toDiffStatus()
        )
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