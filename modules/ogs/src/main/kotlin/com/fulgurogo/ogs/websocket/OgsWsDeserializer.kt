package com.fulgurogo.ogs.websocket

import com.fulgurogo.ogs.websocket.model.GameListRequest.Companion.GAME_LIST_REQUEST_ID
import com.fulgurogo.ogs.websocket.model.OgsWSGameList
import com.fulgurogo.ogs.websocket.model.OgsWsGameData
import com.fulgurogo.ogs.websocket.model.OgsWsMessage
import com.google.gson.*
import java.lang.reflect.Type

class OgsWsDeserializer : JsonDeserializer<OgsWsMessage> {
    private val gson = Gson()

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): OgsWsMessage? {
        val firstItem = json?.asJsonArray?.get(0) as JsonPrimitive
        return if (firstItem.isNumber && firstItem.asInt == GAME_LIST_REQUEST_ID)
            OgsWsMessage.GameList(gson.fromJson(json.asJsonArray?.get(1), OgsWSGameList::class.java))
        else if (firstItem.isString && firstItem.asString.startsWith("game/") && firstItem.asString.endsWith("/gamedata"))
            OgsWsMessage.GameData(gson.fromJson(json.asJsonArray?.get(1), OgsWsGameData::class.java))
        else
            null
    }
}
