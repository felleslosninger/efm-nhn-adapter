package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

val jsonParser = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

/** Java-friendly entry point to [jsonParser]. */
object KxJson {

    @JvmStatic
    fun <T> decode(jsonString: String, serializer: KSerializer<T>): T =
        jsonParser.decodeFromString(serializer, jsonString)

    @JvmStatic
    fun <T> decode(jsonElement: JsonElement, serializer: KSerializer<T>): T =
        jsonParser.decodeFromJsonElement(serializer, jsonElement)

    @JvmStatic fun <T> encode(value: T, serializer: KSerializer<T>): String = jsonParser.encodeToString(serializer, value)
}
