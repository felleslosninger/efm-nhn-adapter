package no.difi.meldingsutveksling.nhn.adapter.config

import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

/**
 * Registers kotlinx.serialization JSON codecs ahead of Jackson for `@Serializable` types. They use the default codec
 * config, so `spring.http.codecs.max-in-memory-size` applies to them as well.
 */
@Configuration
class KotlinxSerializationCodecConfig : CodecCustomizer {
    override fun customize(configurer: CodecConfigurer) {
        configurer.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonDecoder(jsonParser))
        configurer.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonEncoder(jsonParser))
    }
}
