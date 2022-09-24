package com.fulgurogo.features.user.igs

import com.fulgurogo.features.user.ServerUser

class IgsUser(playerInfo: String) : ServerUser() {
    companion object {
        private const val PLAYER_LINE = "9 Player:"
        private const val RATING_LINE = "9 Rating:"
    }

    var name: String? = null
    var ranking: String? = null

    init {
        playerInfo.split("\n").toTypedArray().forEach { line ->
            val nameSet = name.isNullOrBlank().not()
            val rankSet = ranking.isNullOrBlank().not()

            if (nameSet && rankSet) {
                return@forEach
            } else if (!nameSet && line.startsWith(PLAYER_LINE)) {
                name = line.substring(PLAYER_LINE.length).trim()
            } else if (!rankSet && line.startsWith(RATING_LINE)) {
                ranking = line.substring(RATING_LINE.length).trim().split(" ").toTypedArray()[0]
            }
        }
    }

    override fun id(): String? = name
    override fun pseudo(): String? = name
    override fun rank(): String? = ranking
    override fun link(withRank: Boolean): String = "$name${if (withRank) ranking else ""}"
}
