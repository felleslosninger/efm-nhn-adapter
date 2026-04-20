package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import no.difi.meldingsutveksling.nhn.adapter.model.FeilmeldingForApplikasjonskvitteringSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.StatusForMottakAvMeldingSerializer
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding

val jsonParser = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    serializersModule = SerializersModule {
        contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
        contextual(FeilmeldingForApplikasjonskvittering::class, FeilmeldingForApplikasjonskvitteringSerializer)
    }
}

object KxJson {

    @JvmStatic
    fun <T> decode(jsonString: String, serializer: KSerializer<T>): T {
        return jsonParser.decodeFromString(serializer, jsonString)
    }

    @JvmStatic
    fun <T> decode(jsonElement: JsonElement, serializer: KSerializer<T>): T {
        return jsonParser.decodeFromJsonElement(serializer, jsonElement)
    }

    @JvmStatic
    fun <T> encode(value: T, serializer: KSerializer<T>): String {
        return jsonParser.encodeToString(serializer, value)
    }
}