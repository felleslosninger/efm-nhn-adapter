package no.difi.meldingsutveksling.nhn.adapter

import org.springframework.beans.factory.BeanRegistrarDsl
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
        }

    }

})