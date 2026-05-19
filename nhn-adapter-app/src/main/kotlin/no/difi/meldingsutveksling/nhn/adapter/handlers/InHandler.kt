package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartFileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toInMessage
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class InHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
        val inMessages = mshService.getMessagesWithMetadata(receiverHerId, clientContext).map { it.toInMessage() }
        val json = jsonParser.encodeToString(ListSerializer(IncomingMessage.serializer()), inMessages)
        return ServerResponse.ok().json().bodyValueAndAwait(json)
    }

    suspend fun getApplicationReceipt(id: UUID, clientContext: ClientContext): ServerResponse {
        val receipt = mshService.getApplicationReceipt(id, clientContext)
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receipt.receiverHerId))
        val forretningsmelding = parcelService.getForretningsmelding(receipt, clientContext)
        val dokumentpakke = parcelService.getDokumentpakke(receipt, clientContext)

        return ServerResponse.ok()
            .multipartMixed()
            .bodyValueAndAwait(
                MultipartBodyBuilder()
                    .apply {
                        forretningsmelding(forretningsmelding)
                        dokumentpakke(dokumentpakke)
                    }
                    .build()
            )
    }

    suspend fun getBusinessDocument(id: UUID, clientContext: ClientContext): ServerResponse {
        val businessDocument: BusinessDocumentResponse = mshService.getBusinessDocument(id, clientContext)
        val receiverHerId =
            businessDocument.receiver.child.herId.orElseThrowNotFound("ReceiverHerId not found in SBDH!")
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))

        val forretningsmelding = parcelService.getForretningsmelding(businessDocument, clientContext)
        val dokumentpakke = parcelService.getDokumentpakke(businessDocument, clientContext)

        return ServerResponse.ok()
            .multipartMixed()
            .bodyValueAndAwait(
                MultipartBodyBuilder()
                    .apply {
                        forretningsmelding(forretningsmelding)
                        dokumentpakke(dokumentpakke)
                    }
                    .build()
            )
    }

    suspend fun markMessageRead(messageId: UUID, receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
        mshService.markMessageRead(messageId, receiverHerId, clientContext)

        return ServerResponse.ok().textPlain().bodyValueAndAwait("Message deleted")
    }

    private fun MultipartBodyBuilder.forretningsmelding(resource: Resource): MultipartBodyBuilder {
        part(MultipartNames.FORRETNINGSMELDING, resource, MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE))
            .filename(MultipartFileNames.FORRETNINGSMELDING)
        return this
    }

    private fun MultipartBodyBuilder.dokumentpakke(resource: Resource): MultipartBodyBuilder {
        part(MultipartNames.DOKUMENTPAKKE, resource, MediaType.parseMediaType(ContentTypes.APPLICATION_ASICE))
            .filename(MultipartFileNames.DOKUMENTPAKKE)
        return this
    }
}
