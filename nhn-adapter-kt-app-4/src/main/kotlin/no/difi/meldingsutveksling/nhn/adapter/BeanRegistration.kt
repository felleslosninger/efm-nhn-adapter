package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.Serializable
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.http.codec.KotlinSerializationSupport
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter


class BeanRegistration : BeanRegistrarDsl ({
    registerBean<RouterFunction<*>> {

        coRouter {
            GET("/helloWorld") {
                it.awaitBody<String>()
                ServerResponse.ok().bodyValueAndAwait("Hello World")
            }
            POST("/kotlinx") {
                val kotlinX = it.awaitBody<TestKotlinX>()
                ServerResponse.ok().bodyValueAndAwait(kotlinX.copy(name = "Test2"))
            }
        }

    }

})

@Serializable
data class TestKotlinX(val name: String, val value:String)