package com.fulgurogo.fox.api.model

data class FoxApiUser(
    val id: Int = 0,
    val nick: String = "",
    val rank: String = "",
    val ai: Boolean = false
) {
    fun isBot() = ai
}
