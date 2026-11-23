package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.transform.stream.StreamSource
import kotlin.time.toKotlinInstant
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.messages.IncomingAttachment
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingConverter
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils
import no.kith.xmlstds.base64container.Base64Container
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.msghead._2006_05_24.CS
import no.kith.xmlstds.msghead._2006_05_24.CV
import no.kith.xmlstds.msghead._2006_05_24.Ident
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.kith.xmlstds.msghead._2006_05_24.Organisation
import no.kith.xmlstds.msghead._2006_05_24.RefDoc
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.IdType
import no.ks.fiks.hdir.KodeverkRegister
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.hdir.TypeDokumentreferanse
import no.ks.fiks.nhn.edi.XmlContext
import no.ks.fiks.nhn.msh.Address
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.Country
import no.ks.fiks.nhn.msh.County
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonCommunicationParty
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender
import no.ks.fiks.nhn.parseOffsetDateTimeOrNull
import org.springframework.util.MimeType

object BusinessDocumentDeserializer {
    const val MSG_HEAD_ROOT = "MsgHead"
    const val MSG_HEAD_VERSION = "v1.2 2006-05-24"

    private val log = KotlinLogging.logger {}

    fun deserializeMsgHead(xml: String): MsgHead {
        XMLUtils.validateRootElement(xml, MSG_HEAD_ROOT)
        if (XMLUtils.getVersion(xml) != MSG_HEAD_VERSION) {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "Invalid MIGversion. Only $MSG_HEAD_VERSION is supported.",
            )
        }
        val msgHead =
            try {
                XmlContext.validateXml(xml)
                XmlContext.createUnmarshaller().unmarshal(StreamSource(StringReader(xml)), MsgHead::class.java).value
            } catch (e: Exception) {
                // Not e.message: messages from the schema validation may quote values from the
                // message,
                // e.g. the patient's fødselsnummer. The cause has them.
                throw ApplicationReceiptException(
                    FeilmeldingForApplikasjonskvittering.VALIDERINGSFEIL_XML,
                    "The MsgHead is not valid",
                    e,
                )
            }
        if (msgHead.msgInfo == null) {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "Could not find MsgInfo in the provided XML. The message is invalid or of wrong type.",
            )
        }

        return msgHead
    }

    fun MsgHead.getSender() = with(msgInfo.sender.organisation) { Sender(parent = getParent(), child = getChild()) }

    fun MsgHead.getReceiver() =
        with(msgInfo.receiver.organisation) {
            Receiver(parent = getParent(), child = getChild(), patient = getPatient())
        }

    fun Organisation.getParent() =
        OrganizationCommunicationParty(
            ids = ident.getOrganisasjonId(),
            address = convertAddress(),
            name = organisationName,
        )

    /**
     * The child is either a sub-organization or a healthcare professional within the parent organization. Note that a
     * healthcare professional is given the address of the parent organization, not its own.
     */
    fun Organisation.getChild() =
        organisation?.getParent()
            ?: with(
                healthcareProfessional
                    ?: throw invalidMessage(
                        FeilmeldingForApplikasjonskvittering.ANNEN_FEIL_FORMAT,
                        "Found neither a sub-organisation nor a healthcare professional in $organisationName",
                    )
            ) {
                PersonCommunicationParty(
                    ids = ident.getPersonId(),
                    address = this@getChild.convertAddress(),
                    firstName = givenName,
                    middleName = middleName,
                    lastName = familyName,
                )
            }

    private fun Organisation.convertAddress() =
        address?.let { address ->
            Address(
                type = address.type?.let { type -> Adressetype.entries.firstOrNull { it.verdi == type.v } },
                streetAdr = address.streetAdr,
                postalCode = address.postalCode,
                city = address.city,
                postbox = address.postbox,
                county = address.county?.let { County(it.v, it.dn) },
                country = address.country?.let { Country(it.v, it.dn) },
            )
        }

    private fun MsgHead.getPatient() =
        with(
            msgInfo.patient
                ?: throw invalidMessage(
                    FeilmeldingForApplikasjonskvittering.UTILSTREKKELIG_PASIENTOPPLYSNING,
                    "Found no patient",
                )
        ) {
            Patient(
                fnr =
                    ident.getPersonId().firstOrNull()?.id
                        ?: throw invalidMessage(
                            FeilmeldingForApplikasjonskvittering.PASIENT_MANGLER_FNR,
                            "Found no ids for patient",
                        ),
                firstName = givenName,
                middleName = middleName,
                lastName = familyName,
            )
        }

    /** The first document of a MsgHead is the Dialogmelding itself; older versions are converted to the latest. */
    fun MsgHead.getDialogmelding(): Dialogmelding =
        when (val content = document.firstOrNull()?.refDoc?.content?.any?.singleOrNull()) {
            is no.kith.xmlstds.dialog._2006_10_11.Dialogmelding -> DialogmeldingConverter.toLatest(content)
            is Dialogmelding -> content
            null ->
                throw invalidMessage(
                    FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                    "Found no Dialogmelding in MsgHead",
                )
            else ->
                throw invalidMessage(
                    FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                    "Unsupported message type: ${content::class.simpleName}",
                )
        }

    /** The documents following the Dialogmelding are its attachments. Only 'A, Vedlegg' is supported. */
    fun MsgHead.getAttachments(): List<IncomingAttachment> =
        document
            .drop(1)
            .map { it.refDoc }
            .filter { refDoc ->
                (refDoc.msgType?.toTypeDokumentreferanse() == TypeDokumentreferanse.VEDLEGG).also { isVedlegg ->
                    if (!isVedlegg) {
                        log.info { "Ignoring ref doc of type ${refDoc.msgType?.v}, as only 'A, Vedlegg' is supported" }
                    }
                }
            }
            .map { refDoc ->
                IncomingAttachment(
                    issueDate = refDoc.issueDate?.v?.parseOffsetDateTimeOrNull()?.toInstant()?.toKotlinInstant(),
                    description = refDoc.description,
                    mimeType = refDoc.validMimeType(),
                    data = ByteArrayInputStream(refDoc.base64Content().value),
                )
            }

    private fun RefDoc.base64Content(): Base64Container =
        content?.any?.singleOrNull() as? Base64Container
            ?: throw invalidMessage(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "Expected a vedlegg in a Base64Container",
            )

    private fun RefDoc.validMimeType(): String =
        mimeType?.takeIf { runCatching { MimeType.valueOf(it) }.isSuccess }
            ?: throw invalidMessage(
                FeilmeldingForApplikasjonskvittering.ANNEN_FEIL_FORMAT,
                "A vedlegg has no valid MIME type",
            )

    fun MsgHead.getConversationRef() =
        if (
            msgInfo.conversationRef?.refToConversation.isNullOrBlank() &&
                msgInfo.conversationRef?.refToParent.isNullOrBlank()
        ) {
            null
        } else {
            ConversationRef(
                refToParent = msgInfo.conversationRef?.refToParent,
                refToConversation = msgInfo.conversationRef?.refToConversation,
            )
        }

    private fun CS.toTypeDokumentreferanse() = TypeDokumentreferanse.entries.firstOrNull { it.verdi == v }

    // The messages name the type, not the id, which may be a fødselsnummer

    private fun List<Ident>.getPersonId() =
        getId().map {
            it as? PersonId
                ?: throw invalidMessage(
                    FeilmeldingForApplikasjonskvittering.UGYLDIG_ID,
                    "Expected an id of a person, but got one of type ${it.type}",
                )
        }

    private fun List<Ident>.getOrganisasjonId() =
        getId().map {
            it as? OrganizationId
                ?: throw invalidMessage(
                    FeilmeldingForApplikasjonskvittering.UGYLDIG_ID,
                    "Expected an id of an organisation, but got one of type ${it.type}",
                )
        }

    private fun List<Ident>.getId() = map {
        when (val type = it.typeId.toIdType()) {
            is PersonIdType -> PersonId(it.id, type)
            is OrganizationIdType -> OrganizationId(it.id, type)
        }
    }

    private fun CV.toIdType() =
        KodeverkRegister.getKodeverk(s, v) as? IdType
            ?: throw invalidMessage(
                FeilmeldingForApplikasjonskvittering.UGYLDIG_ID,
                "Expected kodeverk of a valid IdType, but got ($s, $v, $dn)",
            )
}

/** The message is invalid: the sender's fault, which the receiver answers with [error] in an application receipt. */
internal fun invalidMessage(error: FeilmeldingForApplikasjonskvittering, message: String) =
    ApplicationReceiptException(error, message)
