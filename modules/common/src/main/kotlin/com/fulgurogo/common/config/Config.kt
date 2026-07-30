package com.fulgurogo.common.config

import java.util.*

object Config {
    private val properties = Properties()

    init {
        properties.load(this::class.java.classLoader.getResourceAsStream("config.properties"))
    }

    fun get(key: String): String = properties.getProperty(key)

    /**
     * The value of a key that is allowed to be absent, null when it is.
     *
     * [get] promises a non-null String, so a missing key fails instead of defaulting — which is what every key the app
     * cannot run without wants. An optional key, typically a dev-only override, has to be read through this one: read
     * through [get] it would take the whole service down on the machines that do not define it.
     */
    fun getOrNull(key: String): String? = properties.getProperty(key)
}
