package no.difi.meldingsutveksling.nhn.adapter.config

import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

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
