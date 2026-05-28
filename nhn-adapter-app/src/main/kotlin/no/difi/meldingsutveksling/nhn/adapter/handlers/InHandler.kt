package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.clientContext
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.extensions.toJWEToken
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.GetDocumentInput
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartFileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toInMessage
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.idporten.logging.audit.AuditEntry
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class InHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val auditLogService: AuditLogService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_MESSAGES_WITH_METADATA)
                .message("Get messages with metadata")
                .clientContext(clientContext)
                .attribute("receiverHerId", receiverHerId)
        ) { auditEntryBuilder ->
            securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
            val inMessages = mshService.getMessagesWithMetadata(receiverHerId, clientContext).map { it.toInMessage() }

            auditEntryBuilder.attribute("messageCount", inMessages.size)

            val json = jsonParser.encodeToString(ListSerializer(IncomingMessage.serializer()), inMessages)
            return ServerResponse.ok().json().bodyValueAndAwait(json)
        }
    }

    suspend fun getApplicationReceipt(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_APPLICATION_RECEIPT)
                .message("Get application receipt")
                .clientContext(clientContext)
        ) { auditEntryBuilder ->
            val jWSObject = parcelService.decryptAndVerify(request.toJWEToken())
            val input = jsonParser.decodeFromString<GetDocumentInput>(jWSObject.payload.toString())
            val certificate = parcelService.getSigningCertificate(jWSObject)
            val id = input.id

            auditEntryBuilder.attribute("id", id)

            val receipt = mshService.getApplicationReceipt(id, clientContext)

            auditEntryBuilder.attribute("receiverHerId", receipt.receiverHerId)
            auditEntryBuilder.attribute("senderHerId", receipt.senderHerId)
            securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receipt.receiverHerId))
            val forretningsmelding = parcelService.getForretningsmelding(receipt, certificate)
            val dokumentpakke = parcelService.getDokumentpakke(receipt, certificate)

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
    }

    suspend fun getBusinessDocument(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_BUSINESS_DOCUMENT)
                .message("Get business document")
                .clientContext(clientContext)
        ) { auditEntryBuilder ->
            val jWSObject = parcelService.decryptAndVerify(request.toJWEToken())
            val input = jsonParser.decodeFromString<GetDocumentInput>(jWSObject.payload.toString())
            val certificate = parcelService.getSigningCertificate(jWSObject)
            val id = input.id

            auditEntryBuilder.attribute("id", id)
            val businessDocument: BusinessDocumentResponse = mshService.getBusinessDocument(id, clientContext)
            val senderHerId = businessDocument.sender.child.herId ?: throw HerIdNotFound()
            val receiverHerId = businessDocument.receiver.child.herId ?: throw HerIdNotFound()
            auditEntryBuilder.attribute("senderHerId", senderHerId)
            auditEntryBuilder.attribute("receiverHerId", receiverHerId)
            securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))

            val forretningsmelding = parcelService.getForretningsmelding(businessDocument, certificate)
            val dokumentpakke = parcelService.getDokumentpakke(businessDocument, certificate)

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
    }

    suspend fun markMessageRead(messageId: UUID, receiverHerId: Int, clientContext: ClientContext): ServerResponse {
        auditLogService.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.MARK_MESSAGE_AS_READ)
                .message("Mark message as read")
                .clientContext(clientContext)
                .attribute("messageId", messageId)
                .attribute("receiverHerId", receiverHerId)
        ) {
            securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))
            mshService.markMessageRead(messageId, receiverHerId, clientContext)
            return ServerResponse.ok().textPlain().bodyValueAndAwait("Message deleted")
        }
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
