package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import java.util.UUID
import kotlinx.serialization.SerializationException
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidIncomingDocumentException
import no.difi.meldingsutveksling.nhn.adapter.errors.fromMshData
import no.difi.meldingsutveksling.nhn.adapter.extensions.multipartMixed
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.extensions.toJWEToken
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.messages.BusinessMessageType
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.GetDocumentInput
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartFileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toInMessage
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.security.access.AccessDeniedException
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
    /** The metadata of a message to [receiverHerId], as referred to by a NewMessage notification. */
    suspend fun getMessage(
        messageId: UUID,
        receiverHerId: Int,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry =
            NHNAdapterAuditIdentifier.GET_MESSAGE.entry(clientContext)
                .attribute("messageId", messageId)
                .attribute("receiverHerId", receiverHerId)

        auditLogService.log(auditEntry) {
            securityService.assertAccessToHerId(clientContext, receiverHerId)
            val message = mshService.getMessage(messageId, clientContext)
            // MSH lets the client get any message to the HER-ids of its organization, not only
            // those to [receiverHerId]
            message.receiverHerIds?.let { receivers ->
                if (receiverHerId !in receivers) {
                    throw AccessDeniedException("Message $messageId is not to $receiverHerId")
                }
            }
            val inMessage = fromMshData { message.toInMessage(receiverHerId) }
            auditEntry.attribute("senderHerId", inMessage.senderHerId)

            val json = jsonParser.encodeToString(IncomingMessage.serializer(), inMessage)
            return ServerResponse.ok().json().bodyValueAndAwait(json)
        }
    }

    /**
     * The business document, for the receiver named in it. Copy receivers are not supported: MSH lists them as
     * receivers of the message, but the business document names the main receiver, so a copy receiver in another
     * organization is denied access.
     */
    suspend fun getBusinessDocument(
        request: ServerRequest,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry = NHNAdapterAuditIdentifier.GET_BUSINESS_DOCUMENT.entry(clientContext)

        auditLogService.log(auditEntry) {
            // Checked again for the receiver once the business document is read, but no MSH call is
            // made without delegation
            securityService.assertAccessToConsumer(clientContext)
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
            val receiverHerId = assertAccessToReceiver(clientContext, document)
            if (receiverHerId != document.sbd.receiverHerId) {
                auditEntry.attribute("fallbackReceiverHerId", receiverHerId)
                document.sbd.receiverHerId = receiverHerId
            }

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

    /**
     * Marks the message downloaded by [receiverHerId], which deletes its business document from MSH. The client
     * deletes its NewMessage notification itself.
     */
    suspend fun markMessageDownloaded(
        messageId: UUID,
        receiverHerId: Int,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry =
            NHNAdapterAuditIdentifier.MARK_MESSAGE_AS_DOWNLOADED.entry(clientContext)
                .attribute("messageId", messageId)
                .attribute("receiverHerId", receiverHerId)

        auditLogService.log(auditEntry) {
            securityService.assertAccessToHerId(clientContext, receiverHerId)
            mshService.markMessageDownloaded(messageId, receiverHerId, clientContext)
            return ServerResponse.ok().textPlain().bodyValueAndAwait("Message marked as downloaded")
        }
    }

    /**
     * Checks access to the receiver of the business document and returns its HER-id. When the receiver's own HER-id is
     * not in Adresseregisteret, e.g. a department that no longer has one, the levels above it in the document are
     * tried in turn: the message is delivered to the first of them that is.
     *
     * @throws InvalidIncomingDocumentException with [FeilmeldingForApplikasjonskvittering.MOTTAKER_FINNES_IKKE] if
     *   none of them is in Adresseregisteret: the client only gave the message id, so the receiver is the sender's
     *   fault
     */
    private suspend fun assertAccessToReceiver(clientContext: ClientContext, document: BusinessDocumentResponse): Int {
        var notFound: HerIdNotFoundException? = null
        for (herId in (listOf(document.sbd.receiverHerId) + document.receiverHerIdFallbacks).distinct()) {
            try {
                securityService.assertAccessToHerId(clientContext, herId)
                return herId
            } catch (e: HerIdNotFoundException) {
                notFound = e
            }
        }
        throw InvalidIncomingDocumentException(
            ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.MOTTAKER_FINNES_IKKE,
                "The receiver of the message is not in Adresseregisteret",
                notFound,
            ),
            isAppRec = document.sbd.type == BusinessMessageType.DIALOGMELDING_KVITTERING.type,
        )
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
