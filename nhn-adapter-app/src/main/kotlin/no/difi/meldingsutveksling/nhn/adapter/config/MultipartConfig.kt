package no.difi.meldingsutveksling.nhn.adapter.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ResourceHttpMessageWriter
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.multipart.MultipartHttpMessageWriter
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class MultipartConfig : WebFluxConfigurer {
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        val multipartWriter = MultipartHttpMessageWriter(listOf(ResourceHttpMessageWriter()))
        configurer.customCodecs().register(multipartWriter)
    }
}
