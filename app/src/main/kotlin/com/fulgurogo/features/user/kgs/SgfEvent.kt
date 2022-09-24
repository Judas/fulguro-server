package com.fulgurogo.features.user.kgs

data class SgfEvent(
    val type: String = "",
    val nodeId: Int = 0,
    val props: MutableList<EventProperties> = mutableListOf()
)

data class EventProperties(
    val timeSystem: String = "none",
    val mainTime: Int = 0, // in seconds
    val rules: String = ""
) {
    fun isLongGame(): Boolean = when (timeSystem) {
        "byo_yomi", "canadian" -> mainTime >= 1200
        else -> false
    }
}

fun MutableList<SgfEvent>.isLongGame(): Boolean =
    isNotEmpty() && first { it.type == "PROP_GROUP_ADDED" && it.nodeId == 0 }
        .props.first { it.rules.isNotBlank() }
        .isLongGame()
