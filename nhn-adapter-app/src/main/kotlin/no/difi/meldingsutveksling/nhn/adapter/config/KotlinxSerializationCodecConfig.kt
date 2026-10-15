package no.difi.meldingsutveksling.nhn.adapter.config

import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

/** Registers kotlinx.serialization JSON codecs ahead of Jackson for `@Serializable` types. */
@Configuration
class KotlinxSerializationCodecConfig : CodecCustomizer {
    override fun customize(configurer: CodecConfigurer) {
        configurer
            .customCodecs()
            .register(KotlinSerializationJsonDecoder(jsonParser).apply { maxInMemorySize = MAX_REQUEST_MEMORY_SIZE })
        configurer.customCodecs().register(KotlinSerializationJsonEncoder(jsonParser))
    }

    companion object {
        const val MAX_REQUEST_MEMORY_SIZE = 18 * 1024 * 1024
    }
}
