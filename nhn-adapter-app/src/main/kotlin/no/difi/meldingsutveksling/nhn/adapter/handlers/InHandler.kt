package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.fromMshData
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.extensions.toJWEToken
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
) {
    suspend fun getMessagesWithMetadata(
        receiverHerId: Int,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry =
            NHNAdapterAuditIdentifier.GET_MESSAGES_WITH_METADATA.entry(clientContext)
                .attribute("receiverHerId", receiverHerId)

        // Clients poll this endpoint frequently, so empty results are not audit logged
        var messageCount = 0
        auditLogService.log(auditEntry, skipOnSuccess = { messageCount == 0 }) {
            securityService.assertAccessToHerId(clientContext, receiverHerId)
            val messages = mshService.getMessagesWithMetadata(receiverHerId, clientContext)
            val inMessages = fromMshData { messages.map { it.toInMessage() } }
            messageCount = inMessages.size
            auditEntry.attribute("messageCount", messageCount)

            val json = jsonParser.encodeToString(ListSerializer(IncomingMessage.serializer()), inMessages)
            return ServerResponse.ok().json().bodyValueAndAwait(json)
        }
    }

    suspend fun getBusinessDocument(
        request: ServerRequest,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry = NHNAdapterAuditIdentifier.GET_BUSINESS_DOCUMENT.entry(clientContext)

        auditLogService.log(auditEntry) {
            val jwsObject = parcelService.decryptAndVerify(request.toJWEToken())
            val input =
                try {
                    jsonParser.decodeFromString<GetDocumentInput>(jwsObject.payload.toString())
                } catch (e: SerializationException) {
                    throw ClientInputException("Invalid input", e)
                }
            val clientCertificate = parcelService.getSigningCertificate(jwsObject)
            auditEntry.attribute("id", input.id)

            val document = mshService.getBusinessDocument(input.id, clientContext)
            auditEntry
                .attribute("senderHerId", document.sbd.senderHerId)
                .attribute("receiverHerId", document.sbd.receiverHerId)
            securityService.assertAccessToHerId(clientContext, document.sbd.receiverHerId)

            val forretningsmelding =
                parcelService.signAndEncrypt(jsonMapper.writeValueAsString(document.sbd), clientCertificate)
            val dokumentpakke =
                document.attachments
                    .takeIf { it.isNotEmpty() }
                    ?.let { parcelService.createAndEncryptAsic(clientCertificate, it) }

            return ServerResponse.ok()
                .multipartMixed()
                .bodyValueAndAwait(
                    MultipartBodyBuilder()
                        .apply {
                            forretningsmelding(ByteArrayResource(forretningsmelding.toByteArray()))
                            dokumentpakke?.let { dokumentpakke(it) }
                        }
                        .build()
                )
        }
    }

    suspend fun markMessageRead(
        messageId: UUID,
        receiverHerId: Int,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry =
            NHNAdapterAuditIdentifier.MARK_MESSAGE_AS_READ.entry(clientContext)
                .attribute("messageId", messageId)
                .attribute("receiverHerId", receiverHerId)

        auditLogService.log(auditEntry) {
            securityService.assertAccessToHerId(clientContext, receiverHerId)
            mshService.markMessageRead(messageId, receiverHerId, clientContext)
            return ServerResponse.ok().textPlain().bodyValueAndAwait("Message marked as read")
        }
    }

    private fun MultipartBodyBuilder.forretningsmelding(resource: Resource) {
        part(MultipartNames.FORRETNINGSMELDING, resource, MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE))
            .filename(MultipartFileNames.FORRETNINGSMELDING)
    }

    private fun MultipartBodyBuilder.dokumentpakke(resource: Resource) {
        part(MultipartNames.DOKUMENTPAKKE, resource, MediaType.parseMediaType(ContentTypes.APPLICATION_ASICE))
            .filename(MultipartFileNames.DOKUMENTPAKKE)
    }
}
