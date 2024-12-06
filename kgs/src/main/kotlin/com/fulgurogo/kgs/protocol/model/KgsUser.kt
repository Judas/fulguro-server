package com.fulgurogo.kgs.protocol.model

data class KgsUser(
    val name: String = "",
    val rank: String = "",
    val authLevel: String = ""
) {
    fun isBot() = authLevel.contains("robot")
}
