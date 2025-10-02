package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.difi.meldingsutveksling.nhn.adapter.model.FeilmeldingForApplikasjonskvitteringSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.IdSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.StatusForMottakAvMeldingSerializer
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.Id
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.WebHandler

fun BeanRegistrarDsl.testCoRouter(block: CoRouterFunctionDsl.(BeanRegistrarDsl.SupplierContextDsl<*>) -> Unit) {
    kotlinXPriority()
    registerBean<WebHandler> {
        RouterFunctions.toWebHandler(coRouter { block.invoke(this@coRouter, this@registerBean) }, bean())
    }
}

fun BeanRegistrarDsl.kotlinXPriority() =
    registerBean<HandlerStrategies>() {
        HandlerStrategies.builder()
            .codecs { codecs ->
                val json = Json {
                    ignoreUnknownKeys = true
                    classDiscriminator = "type"
                    serializersModule = SerializersModule {
                        contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
                        contextual(
                            FeilmeldingForApplikasjonskvittering::class,
                            FeilmeldingForApplikasjonskvitteringSerializer,
                        )
                        contextual(Id::class, IdSerializer)
                    }
                }

                codecs.customCodecs().register(KotlinSerializationJsonEncoder(json))
                codecs.customCodecs().register(KotlinSerializationJsonDecoder(json))
            }
            .build()
    }
