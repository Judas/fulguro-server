package com.fulgurogo.common.utilities

/**
 * Reads a root property out of raw SGF text: `sgf.sgfProperty("SZ")` returns `"19"` for a `SZ[19]` record.
 *
 * Deliberately not a real SGF parser. The servers hand us well-formed single-game records and we only ever want a
 * handful of root properties (`SZ`, `HA`, `KM`, `TM`). Returns null when the key is absent, or when its value is not
 * terminated — the previous copies of this threw StringIndexOutOfBoundsException on the latter.
 */
fun String.sgfProperty(key: String): String? {
    val keyIndex = indexOf("$key[")
    if (keyIndex < 0) return null

    val value = substring(keyIndex + key.length + 1)
    val end = value.indexOf("]")
    return if (end < 0) null else value.substring(0, end)
}
