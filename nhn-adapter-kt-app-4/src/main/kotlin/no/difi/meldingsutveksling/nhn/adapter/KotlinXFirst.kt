package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.json.Json
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

@Configuration
class KotlinXFirst : CodecCustomizer {
    override fun customize(cfg: CodecConfigurer) {
        val json =
            Json {
                ignoreUnknownKeys = true
                classDiscriminator = "type"
            }
        cfg
            .customCodecs()
            .registerWithDefaultConfig(KotlinSerializationJsonDecoder(json))
        cfg
            .customCodecs()
            .registerWithDefaultConfig(KotlinSerializationJsonEncoder(json))
    }
}
