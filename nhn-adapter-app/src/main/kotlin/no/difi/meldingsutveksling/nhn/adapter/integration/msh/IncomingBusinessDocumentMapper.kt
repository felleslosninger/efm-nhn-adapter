package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.TimeZone
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.stream.XMLStreamException
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
import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils
import no.kith.xmlstds.CS
import no.kith.xmlstds.CV
import no.kith.xmlstds.apprec._2012_02_15.AdditionalId
import no.kith.xmlstds.apprec._2012_02_15.HCP
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.CommunicationParty
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.util.MimeType

private val NORWEGIAN_TIME_ZONE = ZoneId.of("Europe/Oslo")

/** The version of the Standard Business Document Header, which is 1.0. */
private const val HEADER_VERSION = "1.0"

/**
 * @property receiverHerIdFallbacks HER-ids of the levels above the receiver in the business document, the nearest
 *   first. The message is delivered to one of them when the receiver's own HER-id is not in Adresseregisteret.
 */
data class BusinessDocumentResponse(
    val sbd: StandardBusinessDocument,
    val attachments: List<Attachment>,
    val receiverHerIdFallbacks: List<Int> = emptyList(),
)

/** Maps a business document received from MSH (a MsgHead or an AppRec) to a [StandardBusinessDocument]. */
object IncomingBusinessDocumentMapper {
    private val FILE_REFERENCE = Regex("<FileReference>[^<]+</FileReference>")

    /** The type of a HER-id, in kodeverk 9051 (organisations) and 8116 (persons) alike. */
    private val HER_ID_TYPE = OrganizationIdType.HER_ID.verdi

    /** @throws ApplicationReceiptException if the business document is invalid, e.g. not well-formed XML */
    fun toBusinessDocumentResponse(xml: String): BusinessDocumentResponse =
        try {
            when (val rootElement = XMLUtils.getRootElement(xml)) {
                ApplicationReceiptDeserializer.APP_REC_ROOT -> fromApplicationReceipt(xml)
                BusinessDocumentDeserializer.MSG_HEAD_ROOT -> fromMsgHead(xml)
                else ->
                    throw ApplicationReceiptException(
                        FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                        "Unsupported root element = $rootElement.",
                    )
            }
        } catch (e: XMLStreamException) {
            // Not e.message, which may quote the document
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                "Not well-formed XML",
                e,
            )
        }

    private fun fromMsgHead(xml: String): BusinessDocumentResponse {
        val msgHead = BusinessDocumentDeserializer.deserializeMsgHead(xml.replace(FILE_REFERENCE, ""))
        val receiver = msgHead.getReceiver()
        val receiverHerId = requireHerId(receiver.child, receiver.parent, "receiver")
        // A parent with an invalid HER-id is ignored when the child has a valid one
        val parentHerId = runCatching { receiver.parent.herIdOrNull("receiver") }.getOrNull()
        val vedlegg = msgHead.getAttachments()

        val sbd =
            standardBusinessDocument(
                    messageType = BusinessMessageType.DIALOGMELDING,
                    instanceId = msgHead.requireMsgId(),
                    createdAt = msgHead.msgInfo.genDate?.toOffsetDateTime(),
                    scopes = conversationScopes(msgHead),
                )
                .apply {
                    senderHerId = msgHead.getSender().let { requireHerId(it.child, it.parent, "sender") }
                    this.receiverHerId = receiverHerId
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

        return BusinessDocumentResponse(
            sbd,
            attachments,
            receiverHerIdFallbacks = listOfNotNull(parentHerId).filter { it != receiverHerId },
        )
    }

    /**
     * The conversation the message belongs to, which the receiver needs to process it. Without a reference to one, the
     * message starts a conversation of its own, as the first message of a dialog does.
     */
    private fun conversationScopes(msgHead: MsgHead): List<Scope> {
        val conversationRef = msgHead.getConversationRef()
        return listOfNotNull(
            processScope(
                BusinessMessageType.DIALOGMELDING,
                ScopeType.CONVERSATION_ID,
                conversationRef?.refToConversation?.trim()?.ifEmpty { null } ?: msgHead.requireMsgId(),
            ),
            conversationRef
                ?.refToParent
                ?.trim()
                ?.ifEmpty { null }
                ?.let { processScope(BusinessMessageType.DIALOGMELDING, ScopeType.PARENT_ID, it) },
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
        val appRecId = requireId(appRec.id, "Id of the AppRec")
        val receiverHerIds = appRec.receiver.hcp.requireHerIds("receiver")

        val sbd =
            standardBusinessDocument(
                    messageType = BusinessMessageType.DIALOGMELDING_KVITTERING,
                    instanceId = appRecId,
                    createdAt = appRec.genDate?.toOffsetDateTime(),
                    scopes =
                        listOf(
                            processScope(
                                BusinessMessageType.DIALOGMELDING_KVITTERING,
                                ScopeType.CONVERSATION_ID,
                                appRecId,
                            )
                        ),
                )
                .apply {
                    senderHerId = appRec.sender.hcp.requireHerIds("sender").first()
                    receiverHerId = receiverHerIds.first()
                    dialogmeldingKvittering =
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = requireId(appRec.originalMsgId?.id, "OriginalMsgId of the AppRec"),
                            status = appRec.status.toKvitteringStatus(),
                            messages = appRec.error.map { it.toKvitteringStatusMessage() },
                            rawReceipt = xml,
                        )
                }

        return BusinessDocumentResponse(sbd, emptyList(), receiverHerIdFallbacks = receiverHerIds.drop(1))
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
                    headerVersion = HEADER_VERSION
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

    private fun MsgHead.requireMsgId(): String = requireId(msgInfo.msgId, "MsgId of the MsgHead")

    /** An id that is blank cannot identify the message; the schema lets it through. */
    private fun requireId(id: String?, name: String): String =
        id?.trim()?.ifEmpty { null }
            ?: throw invalidMessage(FeilmeldingForApplikasjonskvittering.ANNEN_FEIL_FORMAT, "The $name is blank")

    private fun processScope(messageType: BusinessMessageType, scopeType: ScopeType, instanceId: String) =
        Scope().apply {
            type = scopeType.fullname
            identifier = messageType.process
            instanceIdentifier = instanceId
        }

    private fun IncomingAttachment.filename(index: Int) = AttachmentNames.vedlegg(index, mimeType)

    /**
     * The HER-id of the child, else of the parent, as messages are addressed when sent too. Only a child without a
     * HER-id falls back: one with a HER-id that cannot be read is invalid, as the message is addressed to it.
     */
    private fun requireHerId(child: CommunicationParty, parent: CommunicationParty, role: String): Int =
        child.herIdOrNull(role)
            ?: parent.herIdOrNull(role)
            ?: throw invalidMessage(
                FeilmeldingForApplikasjonskvittering.ANNEN_FEIL_FORMAT,
                "Missing HER-id for the $role in MsgHead",
            )

    /**
     * The HER-ids of the party, the most specific first: a person, then a department/service, then the institution
     * itself. The first is the party's HER-id, which must be valid; the others are the levels above it.
     */
    private fun HCP.requireHerIds(role: String): List<Int> {
        val ids =
            if (inst != null) {
                listOfNotNull(
                    inst.hcPerson?.firstOrNull()?.let { herIdOf(it.id, it.typeId, it.additionalId) },
                    inst.dept?.firstOrNull()?.let { herIdOf(it.id, it.typeId, it.additionalId) },
                    herIdOf(inst.id, inst.typeId, inst.additionalId),
                )
            } else {
                listOfNotNull(hcProf?.let { herIdOf(it.id, it.typeId, it.additionalId) })
            }
        val herId =
            ids.firstOrNull()?.toHerIdOrNull()
                ?: throw invalidMessage(
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL_FORMAT,
                    if (ids.isEmpty()) "Missing HER-id for the $role in AppRec"
                    else "Invalid HER-id for the $role in AppRec",
                )
        return (listOf(herId) + ids.drop(1).mapNotNull { it.toHerIdOrNull() }).distinct()
    }

    /**
     * The HER-id of a party in an AppRec: its id if that is typed as a HER-id, else an additional id that is. An id
     * without a type is taken to be a HER-id, as an AppRec identifies its parties by HER-id.
     */
    private fun herIdOf(id: String?, typeId: CS?, additionalIds: List<AdditionalId>?): String? =
        id?.takeIf { typeId == null || typeId.v == HER_ID_TYPE }
            ?: additionalIds?.firstOrNull { it.type?.v == HER_ID_TYPE }?.id

    private fun String.toHerIdOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }

    /** The status is coded in kodeverk 8222. Only the code (V) is reliable, the display name (DN) is e.g. "Avvist". */
    private fun CS.toKvitteringStatus(): DialogmeldingKvitteringStatus =
        when (StatusForMottakAvMelding.entries.firstOrNull { it.verdi == v }) {
            StatusForMottakAvMelding.OK -> DialogmeldingKvitteringStatus.OK
            StatusForMottakAvMelding.AVVIST -> DialogmeldingKvitteringStatus.REJECTED
            StatusForMottakAvMelding.OK_FEIL_I_DELMELDING -> DialogmeldingKvitteringStatus.OK_ERROR_IN_MESSAGE_PART
            null ->
                throw ApplicationReceiptException(
                    FeilmeldingForApplikasjonskvittering.VALIDERINGSFEIL_XML,
                    "Unknown AppRec status",
                )
        }

    private fun CV.toKvitteringStatusMessage() = KvitteringStatusMessage(code = v, text = dn)

    /** Dates without a time zone are interpreted as Norwegian time. */
    private fun XMLGregorianCalendar.toOffsetDateTime(): OffsetDateTime =
        toGregorianCalendar(TimeZone.getTimeZone(NORWEGIAN_TIME_ZONE), null, null).toZonedDateTime().toOffsetDateTime()
}
