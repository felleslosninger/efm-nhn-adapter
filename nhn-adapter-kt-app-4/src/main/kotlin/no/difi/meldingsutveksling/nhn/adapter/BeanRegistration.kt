package no.difi.meldingsutveksling.nhn.adapter

import com.sun.source.tree.CompilationUnitTree
import kotlinx.serialization.Serializable
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.Credentials
import no.ks.fiks.nhn.flr.Environment
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.nhn.schemas.reg.flr.IFlrReadOperations
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

            GET("/arlookup/fastlege/{fnr}") {
                ServerResponse.ok().buildAndAwait()
            }

            GET("/arlookup/organisasjonellernoe/{herId2}") {
                ServerResponse.ok().buildAndAwait()
            }

            POST("/dph/out") {
                val messageOut = it.awaitBody<MessageOut>()
                println("MessageOut recieved ${messageOut.conversationId}")
                ServerResponse.ok().buildAndAwait()
            }

            POST("/flr/test") {
                val flrClinet = FastlegeregisteretClient(Environment.TEST, Credentials(username = "****", password = "*****"))
                val patientGP = flrClinet.getPatientGP("30905895733")
                println(patientGP?.gpHerId)
                ServerResponse.ok().buildAndAwait()
            }

            POST("/ar/test") {
                val arClient = AdresseregisteretClient(
                    no.ks.fiks.nhn.ar.Environment.TEST,
                    no.ks.fiks.nhn.ar.Credentials(username = "*****", password = "*****"))
                val reciever = arClient.lookupHerId(8143060)
                println(reciever?.name)
                ServerResponse.ok().buildAndAwait()
            }
        }

    }

})

@Serializable
data class TestKotlinX(val name: String, val value:String)