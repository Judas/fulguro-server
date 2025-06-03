package com.fulgurogo.common.utilities

fun String.ellipsize(size: Int): String =
    if (length > size) substring(0, size - 3) + "..."
    else this