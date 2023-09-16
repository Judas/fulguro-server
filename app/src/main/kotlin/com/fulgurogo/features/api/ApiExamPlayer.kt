package com.fulgurogo.features.api

data class ApiExamPlayer(
    val discordId: String,
    val name: String,
    val avatar: String,

    val total: Int,
    val participation: Int,
    val community: Int,
    val patience: Int,
    val victory: Int,
    val refinement: Int,
    val performance: Int,
    val achievement: Int,

    val hunter: Boolean,

    val information: Int,
    val lost: Int,
    val ruin: Int,
    val treasure: Int,
    val gourmet: Int,
    val beast: Int,
    val blacklist: Int,
    val head: Int,
    val ratio: Double
)
