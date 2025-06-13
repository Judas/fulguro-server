package com.fulgurogo.ogs.websocket

import com.fulgurogo.ogs.websocket.model.OgsWsMessage
import com.google.gson.GsonBuilder
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class OgsWsClient(val url: String, val listener: Listener) : WebSocketClient(URI(url)) {
    interface Listener {
        fun onOpened()
        fun onClosed(code: Int, reason: String?, remote: Boolean)
        fun onError(e: Exception?)
        fun onGameListResponse(message: OgsWsMessage.GameList)
        fun onGameDataUpdate(message: OgsWsMessage.GameData)
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(OgsWsMessage::class.java, OgsWsDeserializer())
        .create()

    override fun onOpen(handshakedata: ServerHandshake?) {
        listener.onOpened()
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        listener.onClosed(code, reason, remote)
    }

    override fun onMessage(message: String?) {
        if (!message.isNullOrBlank()) {
            val wsMessage = gson.fromJson(message, OgsWsMessage::class.java)
            when (wsMessage) {
                is OgsWsMessage.GameList -> listener.onGameListResponse(wsMessage)
                is OgsWsMessage.GameData -> listener.onGameDataUpdate(wsMessage)
                else -> {
                    // Drop the message, we don't care
                }
            }
        }
    }

    override fun onError(e: Exception?) {
        listener.onError(e)
    }
}
