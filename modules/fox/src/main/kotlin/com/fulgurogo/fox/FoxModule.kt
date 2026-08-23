package com.fulgurogo.fox

object FoxModule {
    const val TAG = "FOX"

    fun init() {
        FoxService().start()
    }
}
