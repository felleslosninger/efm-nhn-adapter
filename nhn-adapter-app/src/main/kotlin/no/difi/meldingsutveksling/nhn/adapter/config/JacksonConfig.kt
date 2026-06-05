package no.difi.meldingsutveksling.nhn.adapter.config

import com.fasterxml.jackson.annotation.JsonInclude
import java.text.SimpleDateFormat
import no.difi.meldingsutveksling.jackson.PartnerIdentifierModule
import no.difi.meldingsutveksling.jackson.StandardBusinessDocumentModule
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper

@Configuration
class JacksonConfig {
    @Bean
    fun jacksonCustomizer(): JsonMapperBuilderCustomizer {
        return JsonMapperBuilderCustomizer { builder: JsonMapper.Builder? ->
            builder!!
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
                .changeDefaultPropertyInclusion { it.withContentInclusion(JsonInclude.Include.NON_NULL) }
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .disable(SerializationFeature.CLOSE_CLOSEABLE)
                .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .defaultDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModules(StandardBusinessDocumentModule(BusinessMessageType::fromType), PartnerIdentifierModule())
        }
    }
}
