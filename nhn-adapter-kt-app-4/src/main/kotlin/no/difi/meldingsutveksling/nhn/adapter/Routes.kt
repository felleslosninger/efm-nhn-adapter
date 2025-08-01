package no.difi.meldingsutveksling.nhn.adapter

import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

fun CoRouterFunctionDsl.testHelloWorld() = {

    this.GET("/helloWorld") {
        it.awaitBody<String>()
        ServerResponse.ok().bodyValueAndAwait("Hello World")
    }
}

fun CoRouterFunctionDsl.testKotlinX()  = {
    POST("/kotlinx") {
        val kotlinX = it.awaitBody<TestKotlinX>()
        ServerResponse.ok().bodyValueAndAwait(kotlinX.copy(name = "Test2"))
    }
}

fun CoRouterFunctionDsl.testKotlinxSealedclass() = {
    POST("/kotlinxsealedclass") {
        val messageOut = it.awaitBody<CommunicationParty>()
        println("Communication Party: $messageOut")
        ServerResponse.ok().bodyValueAndAwait(messageOut)
    }

}

fun CoRouterFunctionDsl.testFlr(flrClient: FastlegeregisteretClient) = {
    POST("/flr/test") {
        //       val flrClinet = FastlegeregisteretClient(Environment.TEST, Credentials(username = "****", password = "*****"))
        val patientGP = flrClient.getPatientGP("16822449879")
        println(patientGP?.gpHerId)
        ServerResponse.ok().buildAndAwait()
    }
}

fun CoRouterFunctionDsl.arLookupByFnr(flrClient: FastlegeregisteretClient,arClient: AdresseregisteretClient) = {
    GET("/arlookup/fastlege/{fnr}") {
        val fnr = it.pathVariable("fnr")
        val gpHerId = flrClient.getPatientGP(fnr)?.gpHerId.orElseThrowNotFound("GP not found for fnr")
        val communicationParty =
            arClient.lookupHerId(gpHerId).orElseThrowNotFound("Comunication party not found in AR")
        val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")
        val arDetails = ArDetails(parentHerId, gpHerId, "testedi-address", "testsertifikat")
        ServerResponse.ok().bodyValueAndAwait(arDetails)
    }
}

fun CoRouterFunctionDsl.arLookupById() =
    GET("/arlookup/organisasjonellernoe/{herId2}") {
        ServerResponse.ok().buildAndAwait()
    }


fun CoRouterFunctionDsl.dphOut() =
    POST("/dph/out") {
        val messageOut = it.awaitBody<MessageOut>()
        println("MessageOut recieved ${messageOut.conversationId}")
        ServerResponse.ok().buildAndAwait()
    }


fun CoRouterFunctionDsl.testAr(arClient: AdresseregisteretClient) =
    POST("/ar/test") {
        //     val arClient = AdresseregisteretClient(
        //         no.ks.fiks.nhn.ar.Environment.TEST,
        //         no.ks.fiks.nhn.ar.Credentials(username = "*****", password = "*****"))
        val reciever = arClient.lookupHerId(8143060)
        println(reciever?.name)
        ServerResponse.ok().buildAndAwait()
    }


