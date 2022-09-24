package com.fulgurogo.features.user

abstract class ServerUser {
    abstract fun id(): String?
    abstract fun pseudo(): String?
    abstract fun rank(): String?
    abstract fun link(withRank: Boolean): String
}
