package no.difi.meldingsutveksling.nhn.adapter

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.handlers.ArHandlers
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.model.toSerializable
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

val logger = KotlinLogging.logger {}

fun CoRouterFunctionDsl.statusCheck(mshClient: Client) =
    GET("/dph/status/{messageId}") { it -> OutHandler.statusHandler(it, mshClient) }

fun CoRouterFunctionDsl.arLookup(flrClient: DecoratingFlrClient, arClient: AdresseregisteretClient) =
    GET("/arlookup/{identifier}") { ArHandlers.arLookup(it, flrClient, arClient) }

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.incomingReciept(mshClient: Client) =
    GET("/dph/in/{messageId}/receipt") {
        val messageId: UUID =
            it.pathVariable("messageId")
                .runCatching { UUID.fromString(this) }
                .getOrElse {
                    return@GET ServerResponse.badRequest().bodyValueAndAwait("Message id is wrong format")
                }
        // @TODO to use onBehalfOf as request parameter exposes details of next
        // authentication/authorization step
        // potentialy put the onBehalfOf orgnummeret enten som Body eller som ekstra claim i maskin
        // to maski tokenet
        val onBehalfOf = it.queryParamOrNull("onBehalfOf")
        val requestParameters =
            onBehalfOf?.let { onBehalfOf ->
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf)))
            }
        val incomingApplicationReceipt = mshClient.getApplicationReceiptsForMessage(messageId, requestParameters)

        ServerResponse.ok().bodyValueAndAwait(incomingApplicationReceipt.map { it.toSerializable() })
    }

fun CoRouterFunctionDsl.dphOut(mshClient: Client, arClient: AdresseregisteretClient) =
    POST("/dph/out") { OutHandler.dphOut(it, arClient, mshClient) }
