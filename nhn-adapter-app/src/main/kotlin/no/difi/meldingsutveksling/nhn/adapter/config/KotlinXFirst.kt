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

@Configuration
class KotlinXFirst : CodecCustomizer {
    override fun customize(cfg: CodecConfigurer) {
        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
            serializersModule = SerializersModule {
                contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
                contextual(FeilmeldingForApplikasjonskvittering::class, FeilmeldingForApplikasjonskvitteringSerializer)
                contextual(Id::class, IdSerializer)
            }
        }
        cfg.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonDecoder(json))
        cfg.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonEncoder(json))
    }
}
