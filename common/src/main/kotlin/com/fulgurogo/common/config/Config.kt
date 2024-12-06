package com.fulgurogo.common.config

import java.util.*

object Config {
    private val properties = Properties()

    init {
        properties.load(this::class.java.classLoader.getResourceAsStream("config.properties"))
    }

    fun get(key: String): String = properties.getProperty(key)
}
