package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import jakarta.xml.bind.JAXBException
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
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
import no.kith.xmlstds.msghead._2006_05_24.HealthcareProfessional
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
import no.ks.fiks.nhn.msh.CommunicationParty
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
import org.xml.sax.SAXException

object BusinessDocumentDeserializer {
    const val MSG_HEAD_ROOT = "MsgHead"
    const val MSG_HEAD_VERSION = "v1.2 2006-05-24"
    private const val MSG_HEAD_NAMESPACE = "http://www.kith.no/xmlstds/msghead/2006-05-24"
    private const val CONTENT_ELEMENT = "Content"
    private val DIALOGMELDING_NAMESPACES =
        setOf("http://www.kith.no/xmlstds/dialog/2006-10-11", "http://www.kith.no/xmlstds/dialog/2013-01-23")

    /** The kodeverk of person ids, which has the D-nummer too, though [PersonIdType] does not. */
    private const val D_NUMMER = "DNR"

    private val log = KotlinLogging.logger {}

    private val contentReaderFactory =
        XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }

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
            } catch (e: SAXException) {
                throw invalidMsgHead(xml, e)
            } catch (e: JAXBException) {
                throw invalidMsgHead(xml, e)
            }
        if (msgHead.msgInfo == null) {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "Could not find MsgInfo in the provided XML. The message is invalid or of wrong type.",
            )
        }

        return msgHead
    }

    /**
     * The schema only accepts the documents the adapter knows, so a message that is not a Dialogmelding fails it too:
     * that is a format the adapter does not support. Not with e.message, which may quote values from the message, e.g.
     * the patient's fødselsnummer. The cause has them.
     */
    private fun invalidMsgHead(xml: String, cause: Exception): ApplicationReceiptException {
        val namespace =
            try {
                firstContentNamespace(xml)
            } catch (e: Exception) {
                return ApplicationReceiptException(
                    FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                    "The MsgHead is not well-formed XML",
                    e,
                )
            }
        return if (namespace in DIALOGMELDING_NAMESPACES) {
            ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.VALIDERINGSFEIL_XML,
                "The MsgHead is not valid",
                cause,
            )
        } else {
            ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "The first document is not a Dialogmelding",
                cause,
            )
        }
    }

    /**
     * The namespace of the content of the first document, which is the message itself. The whole document is read, so
     * that a document that is not well-formed throws, wherever it breaks.
     */
    private fun firstContentNamespace(xml: String): String? {
        val reader = contentReaderFactory.createXMLStreamReader(StringReader(xml))
        var namespace: String? = null
        var inContent = false
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && namespace == null) {
                if (inContent) namespace = reader.namespaceURI ?: ""
                inContent = reader.localName == CONTENT_ELEMENT && reader.namespaceURI == MSG_HEAD_NAMESPACE
            }
        }
        return namespace
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
     * The child is either a sub-organization or a healthcare professional within the parent organization or the
     * sub-organization. Note that a healthcare professional is given the address of the organization it is in, not its
     * own.
     */
    fun Organisation.getChild(): CommunicationParty {
        val subOrganisation = organisation?.getParent()
        // A healthcare professional in the sub-organization is more specific than the
        // sub-organization itself
        val candidates =
            listOfNotNull(
                organisation?.let { it.healthcareProfessional?.toParty(it) },
                subOrganisation,
                healthcareProfessional?.toParty(this),
            )
        // The one with a HER-id, as the message is addressed to it. An organisation addressed at
        // one level only is its
        // own child.
        return candidates.firstOrNull { it.hasHerIdent() }
            ?: subOrganisation
            ?: candidates.firstOrNull()
            ?: getParent()
    }

    /** The schema lets the names be left out, but they are not needed to deliver the message. */
    private fun HealthcareProfessional.toParty(organisation: Organisation) =
        PersonCommunicationParty(
            ids = ident.getPersonId(),
            address = organisation.convertAddress(),
            firstName = givenName.orEmpty(),
            middleName = middleName,
            lastName = familyName.orEmpty(),
        )

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
                    ident.getPatientId()
                        ?: throw invalidMessage(
                            FeilmeldingForApplikasjonskvittering.PASIENT_MANGLER_FNR,
                            "Found no ids for patient",
                        ),
                // The schema lets the names be left out; the id identifies the patient
                firstName = givenName.orEmpty(),
                middleName = middleName,
                lastName = familyName.orEmpty(),
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

    /**
     * The fødselsnummer of the patient, else the D-nummer, which is not in [PersonIdType]. Other ids, e.g. a H-nummer,
     * are not national ids, so the patient has none. The kodeverk may be left out.
     */
    private fun List<Ident>.getPatientId(): String? = patientId(PersonIdType.FNR.verdi) ?: patientId(D_NUMMER)

    private fun List<Ident>.patientId(type: String): String? =
        filter { it.typeId?.v == type && (it.typeId?.s == null || it.typeId?.s == PersonIdType.FNR.kodeverk) }
            .firstNotNullOfOrNull { it.id?.trim()?.ifEmpty { null } }

    /**
     * Ids of types the adapter does not know are left out rather than rejected: the message is valid, and only the
     * HER-ids and the patient's id are needed. A HER-id is one whichever of the two kodeverk it is given in, but one
     * in the party's own kodeverk comes first.
     */
    private fun List<Ident>.getPersonId(): List<PersonId> {
        val ids = getId()
        return ids.filterIsInstance<PersonId>() +
            ids.filter { it.type == OrganizationIdType.HER_ID }.map { PersonId(it.id, PersonIdType.HER_ID) }
    }

    private fun List<Ident>.getOrganisasjonId(): List<OrganizationId> {
        val ids = getId()
        return ids.filterIsInstance<OrganizationId>() +
            ids.filter { it.type == PersonIdType.HER_ID }.map { OrganizationId(it.id, OrganizationIdType.HER_ID) }
    }

    private fun List<Ident>.getId() = mapNotNull { ident ->
        val id = ident.id?.trim().orEmpty()
        when (val type = ident.typeId?.toIdTypeOrNull()) {
            is PersonIdType -> PersonId(id, type)
            is OrganizationIdType -> OrganizationId(id, type)
            else -> null
        }
    }

    private fun CV.toIdTypeOrNull(): IdType? {
        val kodeverk = s ?: return null
        val verdi = v ?: return null
        // getKodeverk throws on a kodeverk it does not know
        return runCatching { KodeverkRegister.getKodeverk(kodeverk, verdi) }.getOrNull() as? IdType
    }
}

/** Whether the party gives a HER-id, readable or not. */
internal fun CommunicationParty.hasHerIdent(): Boolean =
    ids.any { it.type == PersonIdType.HER_ID || it.type == OrganizationIdType.HER_ID }

/**
 * The HER-id the party gives, or null if it gives none.
 *
 * @throws ApplicationReceiptException if the HER-id is not a positive number
 */
internal fun CommunicationParty.herIdOrNull(role: String): Int? {
    val id = ids.firstOrNull { it.type == PersonIdType.HER_ID || it.type == OrganizationIdType.HER_ID } ?: return null
    return id.id.toIntOrNull()?.takeIf { it > 0 }
        ?: throw invalidMessage(FeilmeldingForApplikasjonskvittering.ANNEN_FEIL_FORMAT, "Invalid HER-id for the $role")
}

/** The message is invalid: the sender's fault, which the receiver answers with [error] in an application receipt. */
internal fun invalidMessage(error: FeilmeldingForApplikasjonskvittering, message: String) =
    ApplicationReceiptException(error, message)
