package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.lang.IllegalArgumentException
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.config.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.SerializableApplicationReceiptInfo
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
        val messageId =
            try {
                UUID.fromString(request.pathVariable("messageId"))
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Message id is wrong format", e)
            }
        // @TODO to use onBehalfOf as request parameter exposes details of next
        // authentication/authorization step
        // potentialy put the onBehalfOf orgnummeret enten som Body eller som ekstra claim i maskin
        // to maski tokenet
        val onBehalfOf = request.queryParamOrNull("onBehalfOf")
        val requestParameters =
            onBehalfOf?.let { onBehalfOf ->
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf)))
            } ?: throw IllegalArgumentException("On behalf of organisation is not provided.")

        val incomingApplicationReceipt = mshClient.getApplicationReceiptsForMessage(messageId, requestParameters)

        val payload =
            jsonParser.encodeToString(
                ListSerializer(SerializableApplicationReceiptInfo.serializer()),
                incomingApplicationReceipt.map { it.toSerializable() },
            )

        return ServerResponse.ok().bodyValueAndAwait(payload)
    }
}
