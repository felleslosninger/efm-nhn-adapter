package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.json.Json
import org.springframework.boot.web.codec.CodecCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

@Configuration
class KotlinXFirst : CodecCustomizer {
    override fun customize(cfg: CodecConfigurer) {
        val json = Json {
            ignoreUnknownKeys = false
        }
        cfg.customCodecs()
            .registerWithDefaultConfig(KotlinSerializationJsonDecoder(json))
        cfg.customCodecs()
            .registerWithDefaultConfig(KotlinSerializationJsonEncoder(json))
     //   cfg.defaultCodecs().jackson2JsonDecoder(null)
     //   cfg.defaultCodecs().jackson2JsonEncoder(null)
    }
}
