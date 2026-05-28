package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentMetadata
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingAttachment
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.model.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.toSerializable
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.kith.xmlstds.CV
import no.kith.xmlstds.apprec._2012_02_15.AppRec
import no.kith.xmlstds.apprec._2012_02_15.HCP
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.Sender
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.nhn.msh.v2.model.AppRecError
import no.nhn.msh.v2.model.AppRecStatus
import no.nhn.msh.v2.model.GetBusinessDocumentResponse
import no.nhn.msh.v2.model.PostAppRecRequest
import no.nhn.msh.v2.model.PostMessageRequest

private const val MESSAGE_MAX_BYTES = 50 * 1000 * 1000

private const val CONTENT_TYPE = "application/xml"
private const val CONTENT_TRANSFER_ENCODING = "base64"

class MshService(private val mshClient: Client, private val internalClient: MshInternalClient) {
    suspend fun sendMessage(input: SendMessageInput, clientContext: ClientContext): UUID {
        val businessDocument = BusinessDocumentSerializer.serializeNhnMessage(input)
        val encodedBusinessDocument = Base64.getEncoder().encodeToString(businessDocument.toByteArray())

        if (encodedBusinessDocument.length > MESSAGE_MAX_BYTES) {
            throw IllegalArgumentException("Message too large $MESSAGE_MAX_BYTES bytes")
        }

        return internalClient.postMessage(
            PostMessageRequest()
                .contentType(CONTENT_TYPE)
                .contentTransferEncoding(CONTENT_TRANSFER_ENCODING)
                .businessDocument(encodedBusinessDocument),
            getRequestParameters(clientContext),
        )
    }

    suspend fun getBusinessDocument(id: String, clientContext: ClientContext): BusinessDocumentResponse {
        val xml =
            internalClient
                .getBusinessDocument(UUID.fromString(id), getRequestParameters(clientContext))
                .let(toXML())
                .replace(Regex("<FileReference>[^<]+</FileReference>"), "")

        return BusinessDocumentDeserializer.deserializeMsgHead(xml)
    }

    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): List<MessageWithMetadata> =
        mshClient.getMessagesWithMetadata(receiverHerId, getRequestParameters(clientContext))

    suspend fun getApplicationReceipt(id: String, clientContext: ClientContext): ApplicationReceiptResponse {
        val xml =
            internalClient.getBusinessDocument(UUID.fromString(id), getRequestParameters(clientContext)).let(toXML())
        val appRec = ApplicationReceiptDeserializer.deserializeAppRec(xml)
        return ApplicationReceiptResponse(
            id = appRec.id,
            senderHerId = appRec.sender.hcp.toHerId(),
            receiverHerId = appRec.receiver.hcp.toHerId(),
            appRec = appRec,
            rawReceipt = xml,
        )
    }

    suspend fun sendApplicationReceipt(receipt: OutgoingApplicationReceipt, clientContext: ClientContext): UUID =
        internalClient.postAppRec(
            id = receipt.payload.relatedToMessageId.let { UUID.fromString(it) },
            senderHerId = receipt.senderHerId,
            request = receipt.payload.toPostAppRecRequest(),
            requestParams = getRequestParameters(clientContext),
        )

    suspend fun markMessageRead(id: UUID, receiverHerId: Int, clientContext: ClientContext) =
        mshClient.markMessageRead(id, receiverHerId, getRequestParameters(clientContext))

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        mshClient.getStatus(id, getRequestParameters(clientContext))

    private fun getRequestParameters(clientContext: ClientContext): RequestParameters =
        RequestParameters(HelseIdTokenParameters(getHelseIdTenantParameters(clientContext)))

    private fun getHelseIdTenantParameters(clientContext: ClientContext): HelseIdTenantParameters {
        val parentOrganization = clientContext.supplier?.organizationIdentifier
        val childOrganization = clientContext.consumer.organizationIdentifier

        return if (parentOrganization != null) {
            MultiTenantHelseIdTokenParameters(childOrganization)
        } else {
            SingleTenantHelseIdTokenParameters(childOrganization)
        }
    }
}

private fun HCP.toHerId(): Int = if (inst != null) inst.hcPerson.first().id.toInt() else hcProf.id.toInt()

private fun toXML(): (GetBusinessDocumentResponse) -> String = {
    if (it.contentTransferEncoding != CONTENT_TRANSFER_ENCODING) {
        throw IllegalArgumentException("'${it.contentTransferEncoding}' is not a supported transfer encoding")
    }
    if (it.contentType != CONTENT_TYPE) {
        throw IllegalArgumentException("'${it.contentType}' is not a supported content type")
    }
    String(Base64.getDecoder().decode(it.businessDocument))
}

private fun DialogmeldingKvitteringMessage.toPostAppRecRequest(): PostAppRecRequest {
    val input = this
    return PostAppRecRequest().apply {
        appRecStatus = input.status.let { AppRecStatus.valueOf(it.name) }
        appRecErrorList = input.messages?.map { it.toAppRecError() }
    }
}

private fun KvitteringStatusMessage.toAppRecError(): AppRecError {
    val input = this

    val error =
        FeilmeldingForApplikasjonskvittering.entries.firstOrNull { it.verdi == input.code }
            ?: FeilmeldingForApplikasjonskvittering.UKJENT

    return AppRecError().apply {
        errorCode = error.verdi
        details = input.text
        description = error.navn
        oid = error.kodeverk
    }
}

data class BusinessDocumentResponse(
    val id: String,
    val sender: Sender,
    val receiver: Receiver,
    val dialogmelding: Dialogmelding,
    val attachments: List<IncomingAttachment>,
    val conversationRef: ConversationRef?,
)

fun BusinessDocumentResponse.toSerializable(): IncomingBusinessDocument {
    val metadataFiler =
        this.attachments
            .mapIndexed { index, attachment ->
                AttachmentNames.vedlegg(index, attachment.mimeType) to
                    AttachmentMetadata(
                        issueDate = attachment.issueDate?.toString(),
                        description = attachment.description,
                    )
            }
            .toMap()

    return IncomingBusinessDocument(
        this.id,
        this.sender.child.herId!!,
        this.receiver.child.herId!!,
        this.conversationRef?.refToConversation,
        this.conversationRef?.refToParent,
        DialogmeldingMessage(AttachmentNames.DIALOGMELDING, this.receiver.patient.toSerializable(), metadataFiler),
    )
}

data class ApplicationReceiptResponse(
    val id: String,
    val senderHerId: Int,
    val receiverHerId: Int,
    val appRec: AppRec,
    val rawReceipt: String,
)

fun ApplicationReceiptResponse.toSerializable(): IncomingApplicationReceipt =
    IncomingApplicationReceipt(
        id = this.appRec.id,
        rawReceipt = this.rawReceipt,
        payload =
            DialogmeldingKvitteringMessage(
                relatedToMessageId = this.appRec.originalMsgId.id,
                status = DialogmeldingKvitteringStatus.valueOf(this.appRec.status.dn),
                messages = this.appRec.error.map { it.toSerializable() },
                hoveddokument = AttachmentNames.KVITTERING,
            ),
    )

private fun CV.toSerializable(): KvitteringStatusMessage = KvitteringStatusMessage(code = this.v, text = this.dn)
