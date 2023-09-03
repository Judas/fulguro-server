package com.fulgurogo.features.user.ogs

data class OgsGameList(
    val results: List<OgsGame> = mutableListOf(),
    val previous: String = "",
    val next: String = ""
)
