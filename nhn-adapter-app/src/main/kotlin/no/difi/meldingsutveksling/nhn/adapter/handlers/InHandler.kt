package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.model.toSerializable
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

object InHandler {
    suspend fun incomingApprec(request: ServerRequest, mshClient: Client): ServerResponse {
        val messageId: UUID =
            request
                .pathVariable("messageId")
                .runCatching { UUID.fromString(this) }
                .getOrElse {
                    return ServerResponse.badRequest().bodyValueAndAwait("Message id is wrong format")
                }
        // @TODO to use onBehalfOf as request parameter exposes details of next
        // authentication/authorization step
        // potentialy put the onBehalfOf orgnummeret enten som Body eller som ekstra claim i maskin
        // to maski tokenet
        val onBehalfOf = request.queryParamOrNull("onBehalfOf")
        val requestParameters =
            onBehalfOf?.let { onBehalfOf ->
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf)))
            }
        val incomingApplicationReceipt = mshClient.getApplicationReceiptsForMessage(messageId, requestParameters)

        return ServerResponse.ok().bodyValueAndAwait(incomingApplicationReceipt.map { it.toSerializable() })
    }
}
