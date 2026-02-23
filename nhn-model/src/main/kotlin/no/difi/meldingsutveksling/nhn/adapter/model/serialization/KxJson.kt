package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.difi.meldingsutveksling.nhn.adapter.model.FeilmeldingForApplikasjonskvitteringSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.IdSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.StatusForMottakAvMeldingSerializer
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.Id

val jsonParser = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    serializersModule = SerializersModule {
        contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
        contextual(FeilmeldingForApplikasjonskvittering::class, FeilmeldingForApplikasjonskvitteringSerializer)
        contextual(Id::class, IdSerializer)
    }
}

object KxJson {

    @JvmField
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        classDiscriminator = "type" // important for sealed classes
    }

    @JvmStatic
    fun <T> decode(jsonString: String, serializer: KSerializer<T>): T {
        return json.decodeFromString(serializer, jsonString)
    }

    @JvmStatic
    fun <T> encode(value: T, serializer: KSerializer<T>): String {
        return json.encodeToString(serializer, value)
    }
}