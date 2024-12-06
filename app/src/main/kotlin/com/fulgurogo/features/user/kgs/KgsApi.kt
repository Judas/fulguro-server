package com.fulgurogo.features.user.kgs

import com.fulgurogo.common.Config
import java.util.*

sealed class KgsApi {
    enum class ChannelType {
        LOGIN,
        LOGIN_SUCCESS,
        LOGOUT,

        JOIN_ARCHIVE_REQUEST,
        ARCHIVE_JOIN,

        DETAILS_JOIN_REQUEST,
        DETAILS_JOIN,

        ROOM_LOAD_GAME,
        GAME_JOIN,

        ROOM_JOIN,
        CHANNEL_ALREADY_JOINED,
        JOIN_REQUEST,
        UNJOIN_REQUEST,
        UNJOIN
    }

    sealed class Request {
        data class Login(
            val type: ChannelType = ChannelType.LOGIN,
            val name: String = Config.get("kgs.user.name"),
            val password: String = Config.get("kgs.user.password"),
            val locale: String = "fr_FR"
        )

        data class ArchiveJoin(
            val name: String,
            val type: ChannelType = ChannelType.JOIN_ARCHIVE_REQUEST,
            val locale: String = "fr_FR"
        )

        data class DetailsJoin(
            val name: String,
            val type: ChannelType = ChannelType.DETAILS_JOIN_REQUEST,
            val locale: String = "fr_FR"
        )

        data class LoadGame(
            val timestamp: String,
            val channelId: Int = Config.get("kgs.fgo.room.channel.id").toInt(),
            val type: ChannelType = ChannelType.ROOM_LOAD_GAME,
            val private: Boolean = true
        )

        data class Join(
            val channelId: Int = Config.get("kgs.fgo.room.channel.id").toInt(),
            val type: ChannelType = ChannelType.JOIN_REQUEST,
        )

        data class Unjoin(
            val channelId: Int,
            val type: ChannelType = ChannelType.UNJOIN_REQUEST
        )

        data class Logout(val type: ChannelType = ChannelType.LOGOUT)
    }

    data class Response(val messages: List<Message> = mutableListOf()) {
        fun hasMessageOfType(type: ChannelType): Boolean = messages.any { type == it.type }
        fun getMessageOfType(type: ChannelType): Message? = messages.firstOrNull { type == it.type }
    }

    data class Message(
        val type: ChannelType, // for all messages
        val user: KgsUser, // For ARCHIVE_JOIN & DETAILS_JOIN messages
        val games: MutableList<KgsGame> = mutableListOf(), // For ARCHIVE_JOIN messages
        val regStartDate: Date, // For DETAILS_JOIN messages
        val channelId: Int, // for GAME_JOIN messages
        val sgfEvents: MutableList<SgfEvent> = mutableListOf() // for GAME_JOIN messages
    )
}
