package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.DialogmeldingNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmeldingKvittering
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.extensions.toReceiver
import no.difi.meldingsutveksling.nhn.adapter.extensions.toSender
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.SendMessageInput
import no.difi.meldingsutveksling.nhn.adapter.messages.BusinessMessageType
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.dokumentpakking.PartUtils
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.ResourceUtils
import no.idporten.logging.audit.AuditEntry
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.ks.fiks.nhn.ar.rest.model.CommunicationParty
import no.ks.fiks.nhn.msh.ConversationRef
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json
import tools.jackson.databind.json.JsonMapper

class OutHandler(
    private val jsonMapper: JsonMapper,
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val auditLogService: AuditLogService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getStatus(messageId: UUID, @Parameter(hidden = true) clientContext: ClientContext): ServerResponse {
        val auditEntry = NHNAdapterAuditIdentifier.GET_STATUS.entry(clientContext).attribute("messageId", messageId)

        auditLogService.log(auditEntry) {
            val statuses = mshService.getStatus(messageId, clientContext).map { it.toMessageStatus() }
            return ServerResponse.ok().json().bodyValueAndAwait(statuses)
        }
    }

    suspend fun sendMessage(
        request: ServerRequest,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry = NHNAdapterAuditIdentifier.SEND_MESSAGE.entry(clientContext)

        auditLogService.log(auditEntry) {
            val multipartData = request.multipartData().awaitSingle()
            val sbd = readStandardBusinessDocument(multipartData.requirePart(MultipartNames.FORRETNINGSMELDING))

            auditEntry
                .attribute("type", sbd.type)
                .attribute("messageId", sbd.messageId)
                .attribute("conversationId", sbd.conversationId)
                .attribute("parentId", sbd.parentId)
                .attribute("senderHerId", sbd.senderHerId)
                .attribute("receiverHerId", sbd.receiverHerId)

            // Authorize before any further lookups or decryption of the dokumentpakke
            val sender = securityService.assertAccessToHerId(clientContext, sbd.senderHerId)

            val messageReference =
                when (sbd.type) {
                    BusinessMessageType.DIALOGMELDING.type ->
                        sendDialogmelding(sbd, sender, multipartData, clientContext)
                    BusinessMessageType.DIALOGMELDING_KVITTERING.type ->
                        sendApplicationReceipt(sbd, auditEntry, clientContext)
                    else -> throw IllegalArgumentException("Unsupported message type ${sbd.type}")
                }
            auditEntry.attribute("messageReference", messageReference)

            return ServerResponse.ok().textPlain().bodyValueAndAwait(messageReference.toString())
        }
    }

    private fun readStandardBusinessDocument(forretningsmelding: Part): StandardBusinessDocument {
        val jwsObject = parcelService.decryptAndVerify(PartUtils.toString(forretningsmelding))
        return jsonMapper.readValue(jwsObject.payload.toString(), StandardBusinessDocument::class.java)
    }

    private suspend fun sendDialogmelding(
        sbd: StandardBusinessDocument,
        sender: CommunicationParty,
        multipartData: MultiValueMap<String, Part>,
        clientContext: ClientContext,
    ): UUID {
        val message = sbd.dialogmelding
        val pasient = requireNotNull(message.pasient) { "Dialogmelding is missing pasient" }
        val dokumentpakke = multipartData.requirePart(MultipartNames.DOKUMENTPAKKE)
        val receiverParty = adresseregisteretService.lookupByHerId(sbd.receiverHerId)

        return withContext(Dispatchers.IO) {
            val documents = parcelService.getAttachments(dokumentpakke)
            val dialogmelding = readDialogmelding(documents, message.hoveddokument)
            DialogmeldingDefaults.apply(dialogmelding, receiverParty)

            val input =
                SendMessageInput(
                    id = UUID.fromString(sbd.messageId),
                    sender = sender.toSender(),
                    receiver = receiverParty.toReceiver(pasient),
                    dialogmelding = dialogmelding,
                    vedlegg = documents.filter { it.filename != message.hoveddokument },
                    metadataFiler = message.metadataFiler,
                    conversationRef = sbd.parentId?.let { ConversationRef(it, sbd.conversationId) },
                )

            mshService.sendMessage(input, clientContext)
        }
    }

    private suspend fun sendApplicationReceipt(
        sbd: StandardBusinessDocument,
        auditEntry: AuditEntry.AuditEntryBuilder,
        clientContext: ClientContext,
    ): UUID {
        val receipt = sbd.dialogmeldingKvittering
        auditEntry.attribute("status", receipt.status)
        return withContext(Dispatchers.IO) {
            mshService.sendApplicationReceipt(sbd.senderHerId, receipt, clientContext)
        }
    }

    private fun readDialogmelding(documents: List<Document>, hoveddokument: String): Dialogmelding {
        val document =
            documents.find { it.filename == hoveddokument } ?: throw DialogmeldingNotFoundException(hoveddokument)
        val xml = ResourceUtils.toString(document.resource, StandardCharsets.UTF_8)
        return DialogmeldingDeserializer.deserializeDialogmelding(xml)
    }

    private fun MultiValueMap<String, Part>.requirePart(name: String): Part =
        getFirst(name) ?: throw IllegalArgumentException("Missing multipart part '$name'")
}
