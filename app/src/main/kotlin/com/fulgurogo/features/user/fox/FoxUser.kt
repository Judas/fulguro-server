package com.fulgurogo.features.user.fox

import com.fulgurogo.features.user.ServerUser

data class FoxUser(val uid: Int = 0, val username: String? = "") : ServerUser() {
    var rank: String? = null

    override fun id(): String = uid.toString()
    override fun pseudo(): String? = username
    override fun rank(): String? = rank
    override fun link(withRank: Boolean): String = "${pseudo()}${if (withRank) rank() else ""}"
}
