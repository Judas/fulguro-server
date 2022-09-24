package com.fulgurogo.features.user.kgs

import com.fulgurogo.features.user.ServerUser

data class KgsUser(
    val name: String = "",
    val rank: String = "",
    val authLevel: String = ""
) : ServerUser() {
    fun isBot() = authLevel.contains("robot")
    fun hasStableRank(): Boolean = rank.isNotBlank() && !rank.contains("?")

    override fun id(): String = name
    override fun pseudo(): String = name
    override fun rank(): String = rank
    override fun link(withRank: Boolean): String =
        "[$name${if (withRank) rank else ""}](https://www.gokgs.com/graphPage.jsp?user=$name)"
}
