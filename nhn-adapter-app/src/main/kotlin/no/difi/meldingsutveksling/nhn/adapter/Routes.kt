package no.difi.meldingsutveksling.nhn.adapter

import kotlin.uuid.ExperimentalUuidApi
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.handlers.ArHandlers
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.msh.Client
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.coRouter

val logger = KotlinLogging.logger {}

fun BeanRegistrarDsl.SupplierContextDsl<RouterFunction<*>>.routes() = coRouter {
    testFlr(bean())
    testAr(bean())
    testDphOut(bean(), bean())
    testRespondApprecFralegekontor(bean())
    arLookup(bean(), bean())
    dphOut(bean(), bean())
    statusCheck(bean())
    incomingReciept(bean())
}

fun CoRouterFunctionDsl.statusCheck(mshClient: Client) =
    GET("/dph/status/{messageId}") { OutHandler.statusHandler(it, mshClient) }

fun CoRouterFunctionDsl.arLookup(flrClient: DecoratingFlrClient, arClient: AdresseregisteretClient) =
    GET("/arlookup/{identifier}") { ArHandlers.arLookup(it, flrClient, arClient) }

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.incomingReciept(mshClient: Client) =
    GET("/dph/in/{messageId}/receipt") {
        return@GET InHandler.incomingApprec(it, mshClient)
    }

fun CoRouterFunctionDsl.dphOut(mshClient: Client, arClient: AdresseregisteretClient) =
    POST("/dph/out") { OutHandler.dphOut(it, arClient, mshClient) }
