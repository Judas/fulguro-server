package com.fulgurogo.ogs.api.model

data class OgsApiGameList(
    val results: List<OgsApiGame> = mutableListOf(),
    val previous: String? = "",
    val next: String? = ""
) {
    /**
     * URL of the next page, null when this is the last one. Blank is treated as absent: the field defaults to "" when
     * missing from the payload, and requesting "" would fail as surely as requesting a page that does not exist.
     */
    fun nextRoute(): String? = next?.takeIf { it.isNotBlank() }
}
