package com.fulgurogo.features.user.egf

import com.fulgurogo.features.user.ServerUser
import com.fulgurogo.utilities.InvalidUserException
import java.util.*

class EgfUser(val id: String?, html: String) : ServerUser() {
    companion object {
        private const val NAME_START_TAG = "<span class=\"plain5\">"
        private const val NAME_END_TAG = "<br>"
        private const val RANK_LINE = "<input name=\"grade\""
        private const val RANKING_START = "value=\""
        private const val RANKING_END = "\">"
    }

    var name: String? = null
    var ranking: String? = null

    init {
        html.split("\n").toTypedArray().forEach { line ->
            val nameSet = name.isNullOrBlank().not()
            val rankSet = ranking.isNullOrBlank().not()

            if (nameSet && rankSet) {
                return@forEach
            } else if (nameSet && line.contains(RANK_LINE)) {
                ranking = line
                    .substring(line.indexOf(RANKING_START) + RANKING_START.length, line.lastIndexOf(RANKING_END))
                    .lowercase(Locale.getDefault())
                    .trim()
            } else if (line.contains(NAME_START_TAG) && line.contains(NAME_END_TAG)) {
                val nameString = line
                    .substring(line.indexOf(NAME_START_TAG) + NAME_START_TAG.length, line.indexOf(NAME_END_TAG))
                    .trim()

                if (nameString.isNotBlank()) name = nameString.replace("_", " ")
                else throw InvalidUserException
            }
        }
    }

    override fun id(): String? = id
    override fun pseudo(): String? = name
    override fun rank(): String? = ranking
    override fun link(withRank: Boolean): String =
        "[$name${if (withRank) ranking else ""}](https://www.europeangodatabase.eu/EGD/Player_Card.php?key=$id)"
}
