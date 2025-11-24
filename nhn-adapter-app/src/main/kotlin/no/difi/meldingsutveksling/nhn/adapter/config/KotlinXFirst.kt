package no.difi.meldingsutveksling.nhn.adapter.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.difi.meldingsutveksling.nhn.adapter.model.FeilmeldingForApplikasjonskvitteringSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.IdSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.StatusForMottakAvMeldingSerializer
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.Id
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

val jsonParser = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    serializersModule = SerializersModule {
        contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
        contextual(FeilmeldingForApplikasjonskvittering::class, FeilmeldingForApplikasjonskvitteringSerializer)
        contextual(Id::class, IdSerializer)
    }
}

@Configuration
class KotlinXFirst : CodecCustomizer {
    companion object {
        const val MAX_REQUEST_MEMORY_SIZE = 18 * 1024 * 1024
    }

    override fun customize(cfg: CodecConfigurer) {
        cfg.customCodecs()
            .register(
                KotlinSerializationJsonDecoder(jsonParser).apply { this.maxInMemorySize = MAX_REQUEST_MEMORY_SIZE }
            )
        cfg.customCodecs().register(KotlinSerializationJsonEncoder(jsonParser))
    }
}
