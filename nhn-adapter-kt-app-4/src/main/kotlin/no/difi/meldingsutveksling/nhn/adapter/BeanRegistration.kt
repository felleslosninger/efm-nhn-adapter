package no.difi.meldingsutveksling.nhn.adapter

import com.sun.source.tree.CompilationUnitTree
import kotlinx.serialization.Serializable
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.http.codec.KotlinSerializationSupport
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
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

            POST("/kotlinxsealedclass") {
                val messageOut = it.awaitBody<CommunicationParty>()
                println("Communication Party: $messageOut")
                ServerResponse.ok().bodyValueAndAwait(messageOut)
            }

            POST("/dph/out") {
                val messageOut = it.awaitBody<MessageOut>()
                println("MessageOut recieved ${messageOut.conversationId}")
                ServerResponse.ok().buildAndAwait()
            }
        }

    }

})

@Serializable
data class TestKotlinX(val name: String, val value:String)