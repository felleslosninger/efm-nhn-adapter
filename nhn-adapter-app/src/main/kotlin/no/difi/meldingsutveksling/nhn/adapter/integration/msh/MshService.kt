package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.domain.sbdh.BusinessScope
import no.difi.meldingsutveksling.domain.sbdh.DocumentIdentification
import no.difi.meldingsutveksling.domain.sbdh.Scope
import no.difi.meldingsutveksling.domain.sbdh.ScopeType
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocumentHeader
import no.difi.meldingsutveksling.nhn.adapter.config.AttachmentMetadata
import no.difi.meldingsutveksling.nhn.adapter.config.BusinessMessageType
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.config.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.config.PROCESS
import no.difi.meldingsutveksling.nhn.adapter.config.Pasient
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmeldingKvittering
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.handlers.Attachment
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getAttachments
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getConversationRef
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getDialogmelding
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getReceiver
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getSender
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.kith.xmlstds.CV
import no.kith.xmlstds.apprec._2012_02_15.HCP
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.ar.toOffsetDateTime
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.nhn.msh.v2.model.AppRecError
import no.nhn.msh.v2.model.AppRecStatus
import no.nhn.msh.v2.model.GetBusinessDocumentResponse
import no.nhn.msh.v2.model.PostAppRecRequest
import no.nhn.msh.v2.model.PostMessageRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.util.MimeType

private const val MESSAGE_MAX_BYTES = 50 * 1000 * 1000

private const val CONTENT_TYPE = "application/xml"
private const val CONTENT_TRANSFER_ENCODING = "base64"

class MshService(private val mshClient: Client, private val internalClient: MshInternalClient) {
    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): List<MessageWithMetadata> =
        mshClient.getMessagesWithMetadata(receiverHerId, getRequestParameters(clientContext))

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        mshClient.getStatus(id, getRequestParameters(clientContext))

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

    suspend fun sendApplicationReceipt(
        senderHerId: Int,
        receipt: DialogmeldingKvitteringMessage,
        clientContext: ClientContext,
    ): UUID =
        internalClient.postAppRec(
            id = receipt.relatedToMessageId.let { UUID.fromString(it) },
            senderHerId = senderHerId,
            request = receipt.toPostAppRecRequest(),
            requestParams = getRequestParameters(clientContext),
        )

    suspend fun getBusinessDocument(id: String, clientContext: ClientContext): BusinessDocumentResponse {
        val businessDocument =
            internalClient.getBusinessDocument(UUID.fromString(id), getRequestParameters(clientContext))

        val xml = businessDocument.let(toXML())

        return when (val rootElement = XMLUtils.getRootElement(xml)) {
            ApplicationReceiptDeserializer.APP_REC_ROOT -> getApplicationReceipt(xml)
            BusinessDocumentDeserializer.MSG_HEAD_ROOT -> getBusinessMessage(xml)
            else ->
                throw ApplicationReceiptException(
                    FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                    "Unsupported root element = $rootElement.",
                )
        }
    }

    private fun getBusinessMessage(xml: String): BusinessDocumentResponse {
        val msgHead =
            BusinessDocumentDeserializer.deserializeMsgHead(
                xml.replace(Regex("<FileReference>[^<]+</FileReference>"), "")
            )

        return BusinessDocumentResponse(
            StandardBusinessDocument().apply {
                standardBusinessDocumentHeader =
                    StandardBusinessDocumentHeader().apply {
                        documentIdentification =
                            DocumentIdentification().apply {
                                instanceIdentifier = msgHead.msgInfo.msgId
                                type = BusinessMessageType.DIALOGMELDING.type
                                standard = BusinessMessageType.DIALOGMELDING.standard
                                typeVersion = BusinessMessageType.DIALOGMELDING.version
                                msgHead.msgInfo.genDate?.let { genDate ->
                                    creationDateAndTime = genDate.toOffsetDateTime()
                                }
                            }

                        businessScope =
                            BusinessScope().apply {
                                msgHead.getConversationRef()?.let { conversationRef ->
                                    addScope(
                                        Scope().apply {
                                            type = ScopeType.CONVERSATION_ID.fullname
                                            identifier = PROCESS
                                            instanceIdentifier =
                                                conversationRef.refToConversation ?: msgHead.msgInfo.msgId
                                        }
                                    )
                                    conversationRef.refToParent?.let { refToParent ->
                                        addScope(
                                            Scope().apply {
                                                type = ScopeType.PARENT_ID.fullname
                                                identifier = PROCESS
                                                instanceIdentifier = refToParent
                                            }
                                        )
                                    }
                                }
                            }
                    }

                senderHerId = msgHead.getSender().child.herId!!
                receiverHerId = msgHead.getReceiver().child.herId!!

                dialogmelding =
                    DialogmeldingMessage(
                        hoveddokument = AttachmentNames.DIALOGMELDING,
                        pasient =
                            msgHead.getReceiver().patient.let {
                                Pasient(it.fnr, it.firstName, it.middleName, it.lastName)
                            },
                        metadataFiler =
                            msgHead
                                .getAttachments()
                                .mapIndexed { index, attachment ->
                                    AttachmentNames.vedlegg(index, attachment.mimeType) to
                                        AttachmentMetadata(
                                            issueDate = attachment.issueDate?.toString(),
                                            description = attachment.description,
                                        )
                                }
                                .toMap(),
                    )
            },
            getAttachments(msgHead),
        )
    }

    private fun getAttachments(msgHead: MsgHead): List<Attachment> {
        val xml = DialogmeldingSerializer.serializeDialogmelding(msgHead.getDialogmelding())
        val attachments = ArrayList<Attachment>()
        attachments.add(
            Attachment(
                AttachmentNames.DIALOGMELDING,
                ByteArrayResource(xml.encodeToByteArray()),
                MimeType.valueOf(MediaType.APPLICATION_XML_VALUE),
            )
        )
        attachments.addAll(
            msgHead.getAttachments().mapIndexed { index, attachment ->
                Attachment(
                    AttachmentNames.vedlegg(index, attachment.mimeType),
                    InputStreamResource(attachment.data!!),
                    MimeType.valueOf(attachment.mimeType),
                )
            }
        )

        return attachments
    }

    private fun getApplicationReceipt(xml: String): BusinessDocumentResponse {
        val appRec = ApplicationReceiptDeserializer.deserializeAppRec(xml)

        return BusinessDocumentResponse(
            StandardBusinessDocument().apply {
                standardBusinessDocumentHeader =
                    StandardBusinessDocumentHeader().apply {
                        documentIdentification =
                            DocumentIdentification().apply {
                                instanceIdentifier = appRec.id
                                type = BusinessMessageType.DIALOGMELDING_KVITTERING.type
                                standard = BusinessMessageType.DIALOGMELDING_KVITTERING.standard
                                typeVersion = BusinessMessageType.DIALOGMELDING_KVITTERING.version
                                appRec.genDate?.let { genDate -> creationDateAndTime = genDate.toOffsetDateTime() }
                            }

                        businessScope =
                            BusinessScope().apply {
                                addScope(
                                    Scope().apply {
                                        type = ScopeType.CONVERSATION_ID.fullname
                                        identifier = PROCESS
                                        instanceIdentifier = appRec.id
                                    }
                                )
                            }
                    }
                senderHerId = appRec.sender.hcp.toHerId()
                receiverHerId = appRec.receiver.hcp.toHerId()
                dialogmeldingKvittering =
                    DialogmeldingKvitteringMessage(
                        relatedToMessageId = appRec.originalMsgId.id,
                        status = DialogmeldingKvitteringStatus.valueOf(appRec.status.dn),
                        messages = appRec.error.map { it.toSerializable() },
                        rawReceipt = xml,
                    )
            },
            listOf(),
        )
    }

    suspend fun markMessageRead(id: UUID, receiverHerId: Int, clientContext: ClientContext) =
        mshClient.markMessageRead(id, receiverHerId, getRequestParameters(clientContext))

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

data class BusinessDocumentResponse(val sbd: StandardBusinessDocument, val attachments: List<Attachment>)

private fun CV.toSerializable(): KvitteringStatusMessage = KvitteringStatusMessage(code = this.v, text = this.dn)
