package com.fulgurogo.features.user.ffg

import com.fulgurogo.features.user.ServerUser
import com.fulgurogo.utilities.InvalidUserException
import java.util.*

class FfgUser(val id: String?, html: String) : ServerUser() {
    companion object {
        private const val UNKNOWN_USER = "<body>Param�tre invalide</body>"
        private const val MAIN_CONTENT_START_TAG = "<div id=\"ffg_main_content\">"
        private const val NAME_START_TAG = "<h3>"
        private const val NAME_END_TAG = "</h3>"
        private const val RANKING_START = "<div><b>Échelle principale</b> : "
        private const val RANKING_END = "</div>"
    }

    var name: String? = null
    var ranking: String? = null

    init {
        var nameStartFound = false

        html.split("\n").toTypedArray().forEach { line ->
            val nameSet = name.isNullOrBlank().not()
            val rankSet = ranking.isNullOrBlank().not()
            if (nameSet && rankSet) return@forEach
            else if (nameSet && line.contains(RANKING_START)) ranking = line
                .substring(line.indexOf(RANKING_START) + RANKING_START.length, line.indexOf(RANKING_END))
                .lowercase(Locale.getDefault())
                .trim()
            else if (nameStartFound && line.contains(NAME_START_TAG)) name = line
                .substring(line.indexOf(NAME_START_TAG) + NAME_START_TAG.length, line.indexOf(NAME_END_TAG))
                .trim()
            else if (line.contains(MAIN_CONTENT_START_TAG)) nameStartFound = true
            else if (line.contains(UNKNOWN_USER)) throw InvalidUserException
        }
    }

    override fun id(): String? = id
    override fun pseudo(): String? = name
    override fun rank(): String? = ranking
    override fun link(withRank: Boolean): String =
        "[$name${if (withRank) ranking else ""}](https://ffg.jeudego.org/php/affichePersonne.php?id=$id)"
}
