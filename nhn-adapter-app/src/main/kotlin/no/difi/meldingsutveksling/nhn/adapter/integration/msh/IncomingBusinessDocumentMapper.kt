package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.time.OffsetDateTime
import no.difi.meldingsutveksling.domain.sbdh.BusinessScope
import no.difi.meldingsutveksling.domain.sbdh.DocumentIdentification
import no.difi.meldingsutveksling.domain.sbdh.Scope
import no.difi.meldingsutveksling.domain.sbdh.ScopeType
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocumentHeader
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmeldingKvittering
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getAttachments
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getConversationRef
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getDialogmelding
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getReceiver
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getSender
import no.difi.meldingsutveksling.nhn.adapter.messages.AttachmentMetadata
import no.difi.meldingsutveksling.nhn.adapter.messages.BusinessMessageType
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.IncomingAttachment
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.PROCESS
import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils
import no.kith.xmlstds.CS
import no.kith.xmlstds.CV
import no.kith.xmlstds.apprec._2012_02_15.HCP
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.ar.toOffsetDateTime
import no.ks.fiks.nhn.msh.CommunicationParty
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.util.MimeType

data class BusinessDocumentResponse(val sbd: StandardBusinessDocument, val attachments: List<Attachment>)

/** Maps a business document received from MSH (a MsgHead or an AppRec) to a [StandardBusinessDocument]. */
object IncomingBusinessDocumentMapper {
    private val FILE_REFERENCE = Regex("<FileReference>[^<]+</FileReference>")

    fun toBusinessDocumentResponse(xml: String): BusinessDocumentResponse =
        when (val rootElement = XMLUtils.getRootElement(xml)) {
            ApplicationReceiptDeserializer.APP_REC_ROOT -> fromApplicationReceipt(xml)
            BusinessDocumentDeserializer.MSG_HEAD_ROOT -> fromMsgHead(xml)
            else ->
                throw ApplicationReceiptException(
                    FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                    "Unsupported root element = $rootElement.",
                )
        }

    private fun fromMsgHead(xml: String): BusinessDocumentResponse {
        val msgHead = BusinessDocumentDeserializer.deserializeMsgHead(xml.replace(FILE_REFERENCE, ""))
        val receiver = msgHead.getReceiver()
        val vedlegg = msgHead.getAttachments()

        val sbd =
            standardBusinessDocument(
                    messageType = BusinessMessageType.DIALOGMELDING,
                    instanceId = msgHead.msgInfo.msgId,
                    createdAt = msgHead.msgInfo.genDate?.toOffsetDateTime(),
                    scopes = conversationScopes(msgHead),
                )
                .apply {
                    senderHerId = msgHead.getSender().child.requireHerId("sender")
                    receiverHerId = receiver.child.requireHerId("receiver")
                    dialogmelding =
                        DialogmeldingMessage(
                            hoveddokument = AttachmentNames.DIALOGMELDING,
                            pasient = with(receiver.patient) { Pasient(fnr, firstName, middleName, lastName) },
                            metadataFiler =
                                vedlegg
                                    .mapIndexed { index, it ->
                                        it.filename(index) to
                                            AttachmentMetadata(it.issueDate?.toString(), it.description)
                                    }
                                    .toMap(),
                        )
                }

        val attachments =
            listOf(dialogmeldingAttachment(msgHead.getDialogmelding())) +
                vedlegg.mapIndexed { index, it ->
                    Attachment(it.filename(index), InputStreamResource(it.data), MimeType.valueOf(it.mimeType))
                }

        return BusinessDocumentResponse(sbd, attachments)
    }

    private fun conversationScopes(msgHead: MsgHead): List<Scope> {
        val conversationRef = msgHead.getConversationRef() ?: return emptyList()
        return listOfNotNull(
            processScope(ScopeType.CONVERSATION_ID, conversationRef.refToConversation ?: msgHead.msgInfo.msgId),
            conversationRef.refToParent?.let { processScope(ScopeType.PARENT_ID, it) },
        )
    }

    private fun dialogmeldingAttachment(dialogmelding: Dialogmelding) =
        Attachment(
            AttachmentNames.DIALOGMELDING,
            ByteArrayResource(DialogmeldingSerializer.serializeDialogmelding(dialogmelding).encodeToByteArray()),
            MimeType.valueOf(MediaType.APPLICATION_XML_VALUE),
        )

    private fun fromApplicationReceipt(xml: String): BusinessDocumentResponse {
        val appRec = ApplicationReceiptDeserializer.deserializeAppRec(xml)

        val sbd =
            standardBusinessDocument(
                    messageType = BusinessMessageType.DIALOGMELDING_KVITTERING,
                    instanceId = appRec.id,
                    createdAt = appRec.genDate?.toOffsetDateTime(),
                    scopes = listOf(processScope(ScopeType.CONVERSATION_ID, appRec.id)),
                )
                .apply {
                    senderHerId = appRec.sender.hcp.toHerId()
                    receiverHerId = appRec.receiver.hcp.toHerId()
                    dialogmeldingKvittering =
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = appRec.originalMsgId.id,
                            status = appRec.status.toKvitteringStatus(),
                            messages = appRec.error.map { it.toKvitteringStatusMessage() },
                            rawReceipt = xml,
                        )
                }

        return BusinessDocumentResponse(sbd, emptyList())
    }

    private fun standardBusinessDocument(
        messageType: BusinessMessageType,
        instanceId: String,
        createdAt: OffsetDateTime?,
        scopes: List<Scope>,
    ) =
        StandardBusinessDocument().apply {
            standardBusinessDocumentHeader =
                StandardBusinessDocumentHeader().apply {
                    documentIdentification =
                        DocumentIdentification().apply {
                            instanceIdentifier = instanceId
                            type = messageType.type
                            standard = messageType.standard
                            typeVersion = messageType.version
                            createdAt?.let { creationDateAndTime = it }
                        }
                    businessScope = BusinessScope().apply { scopes.forEach { addScope(it) } }
                }
        }

    private fun processScope(scopeType: ScopeType, instanceId: String) =
        Scope().apply {
            type = scopeType.fullname
            identifier = PROCESS
            instanceIdentifier = instanceId
        }

    private fun IncomingAttachment.filename(index: Int) = AttachmentNames.vedlegg(index, mimeType)

    private fun CommunicationParty.requireHerId(role: String): Int =
        checkNotNull(herId) { "Missing HER-id for the $role in MsgHead" }

    /** The most specific party wins: a person, then a department/service, then the institution itself. */
    private fun HCP.toHerId(): Int =
        if (inst != null) {
            (inst.hcPerson?.firstOrNull()?.id ?: inst.dept?.firstOrNull()?.id ?: inst.id).toInt()
        } else {
            hcProf.id.toInt()
        }

    /** The status is coded in kodeverk 8222. Only the code (V) is reliable, the display name (DN) is e.g. "Avvist". */
    private fun CS.toKvitteringStatus(): DialogmeldingKvitteringStatus =
        when (StatusForMottakAvMelding.entries.firstOrNull { it.verdi == v }) {
            StatusForMottakAvMelding.OK -> DialogmeldingKvitteringStatus.OK
            StatusForMottakAvMelding.AVVIST -> DialogmeldingKvitteringStatus.REJECTED
            StatusForMottakAvMelding.OK_FEIL_I_DELMELDING -> DialogmeldingKvitteringStatus.OK_ERROR_IN_MESSAGE_PART
            null ->
                throw ApplicationReceiptException(
                    FeilmeldingForApplikasjonskvittering.VALIDERINGSFEIL_XML,
                    "Unknown AppRec status V=$v DN=$dn",
                )
        }

    private fun CV.toKvitteringStatusMessage() = KvitteringStatusMessage(code = v, text = dn)
}
