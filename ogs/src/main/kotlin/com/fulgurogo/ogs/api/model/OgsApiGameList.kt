package com.fulgurogo.ogs.api.model

data class OgsApiGameList(
    val results: List<OgsApiGame> = mutableListOf(),
    val previous: String? = "",
    val next: String? = ""
)
