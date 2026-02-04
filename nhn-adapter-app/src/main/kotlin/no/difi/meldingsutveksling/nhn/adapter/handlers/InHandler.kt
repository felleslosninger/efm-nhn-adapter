package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.ktor.util.encodeBase64
import java.lang.IllegalArgumentException
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.config.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.crypto.EncryptionException
import no.difi.meldingsutveksling.nhn.adapter.crypto.Kryptering
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnTrustStore
import no.difi.meldingsutveksling.nhn.adapter.model.EncryptedFagmelding
import no.difi.meldingsutveksling.nhn.adapter.model.toSerializable
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

object InHandler {
    suspend fun incomingApprec(
        request: ServerRequest,
        mshClient: Client,
        kryptering: Kryptering,
        trustStore: NhnTrustStore,
    ): ServerResponse {
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

        val kid =
            request.queryParamOrNull("kid")
                ?: throw EncryptionException("Request is missing encryption certificate kid.")

        val incomingApplicationReceipt = mshClient.getApplicationReceiptsForMessage(messageId, requestParameters)

        val fagmelding =
            incomingApplicationReceipt
                .map { jsonParser.encodeToString(it.toSerializable()) }
                .map {
                    EncryptedFagmelding(
                        trustStore.getCertificateByKid(kid).encoded.encodeBase64(),
                        kryptering.krypter(it.toByteArray(), trustStore.getCertificateByKid(kid)).encodeBase64(),
                    )
                }
        val payload = jsonParser.encodeToString(ListSerializer(EncryptedFagmelding.serializer()), fagmelding)

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(payload)
    }
}
