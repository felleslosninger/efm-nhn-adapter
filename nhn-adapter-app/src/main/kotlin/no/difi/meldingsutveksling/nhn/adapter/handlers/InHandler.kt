package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.audit.clientContext
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json
import tools.jackson.databind.json.JsonMapper

class InHandler(
    private val jsonMapper: JsonMapper,
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
            val response: BusinessDocumentResponse = mshService.getBusinessDocument(id, clientContext)
            val senderHerId = response.sbd.senderHerId
            val receiverHerId = response.sbd.receiverHerId
            auditEntryBuilder.attribute("senderHerId", senderHerId)
            auditEntryBuilder.attribute("receiverHerId", receiverHerId)
            securityService.assertAccess(clientContext, adresseregisteretService.lookupByHerId(receiverHerId))

            val json = jsonMapper.writeValueAsString(response.sbd)
            val jwe = parcelService.signAndEncrypt(json, certificate)
            val forretningsmelding = ByteArrayResource(jwe.toByteArray(StandardCharsets.UTF_8))

            return ServerResponse.ok()
                .multipartMixed()
                .bodyValueAndAwait(
                    MultipartBodyBuilder()
                        .apply {
                            forretningsmelding(forretningsmelding)

                            if (response.attachments.isNotEmpty()) {
                                val dokumentpakke =
                                    parcelService.createAndEncryptAsic(certificate, response.attachments)
                                dokumentpakke(dokumentpakke)
                            }
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
