package com.fulgurogo.utilities

fun String.ellipsize(size: Int) = if (length > size) substring(0, size - 3) + "..." else this
