package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.transform.stream.StreamSource
import kotlin.time.toKotlinInstant
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.config.IncomingAttachment
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

object BusinessDocumentDeserializer {
    const val MSG_HEAD_ROOT = "MsgHead"
    private const val MSG_HEAD_VERSION = "v1.2 2006-05-24"

    private val log = KotlinLogging.logger {}

    fun deserializeMsgHead(xml: String): MsgHead {
        XMLUtils.validateRootElement(xml, MSG_HEAD_ROOT)
        if (XMLUtils.getVersion(xml) != MSG_HEAD_VERSION) {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "Invalid MIGversion. Only $MSG_HEAD_VERSION is supported.",
            )
        }
        try {
            XmlContext.validateXml(xml)
        } catch (t: Throwable) {
            throw ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.UGYLDIG_XML, t.message)
        }

        val msgHead =
            XmlContext.createUnmarshaller().unmarshal(StreamSource(StringReader(xml)), MsgHead::class.java).value
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

    fun Organisation.getChild() =
        organisation?.let {
            with(organisation) {
                OrganizationCommunicationParty(
                    ids = ident.getOrganisasjonId(),
                    address = convertAddress(),
                    name = it.organisationName,
                )
            }
        }
            ?: with(healthcareProfessional) {
                PersonCommunicationParty(
                    ids = ident.getPersonId(),
                    address = convertAddress(),
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
        with(msgInfo.patient) {
            Patient(
                fnr =
                    ident.getPersonId().let { ids ->
                        ids.firstOrNull()?.id ?: throw IllegalArgumentException("Found multiple ids for patient: $ids")
                    },
                firstName = givenName,
                middleName = middleName,
                lastName = familyName,
            )
        }

    fun MsgHead.getDialogmelding(): Dialogmelding =
        document.firstOrNull()?.refDoc?.content?.any?.singleOrNull()?.let {
            when (it) {
                is no.kith.xmlstds.dialog._2006_10_11.Dialogmelding -> DialogmeldingConverter.toLatest(it)
                is Dialogmelding -> it
                else -> throw IllegalArgumentException("Unsupported message type: $it")
            }
        }!!

    fun MsgHead.getAttachments() =
        document.drop(1).mapIndexedNotNull { _, doc ->
            doc.refDoc.let { refDoc ->
                refDoc
                    .takeIf { refDoc.msgType?.toTypeDokumentreferanse() == TypeDokumentreferanse.VEDLEGG }
                    .also {
                        if (it == null) {
                            log.info {
                                "Ignoring ref doc of type ${refDoc.msgType.v}, as only 'A, Vedlegg' is supported"
                            }
                        }
                    }
                    ?.let {
                        IncomingAttachment(
                            issueDate =
                                refDoc.issueDate?.v?.parseOffsetDateTimeOrNull()?.toInstant()?.toKotlinInstant(),
                            description = refDoc.description,
                            mimeType = refDoc.mimeType,
                            data =
                                (refDoc.content.any.single() as? Base64Container)?.let {
                                    ByteArrayInputStream(it.value)
                                }
                                    ?: throw IllegalArgumentException(
                                        "Expected Base64Container, but got ${refDoc.content}"
                                    ),
                        )
                    }
            }
        }

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

    private fun List<Ident>.getPersonId() =
        getId().map {
            it as? PersonId ?: throw IllegalArgumentException("Expected id with type PersonId, but got $it")
        }

    private fun List<Ident>.getOrganisasjonId() =
        getId().map {
            it as? OrganizationId
                ?: throw IllegalArgumentException("Expected id with type OrganisasjonId, but got $it")
        }

    private fun List<Ident>.getId() = map {
        when (val type = it.typeId.toIdType()) {
            is PersonIdType -> PersonId(it.id, type)
            is OrganizationIdType -> OrganizationId(it.id, type)
        }
    }

    private fun CV.toIdType() =
        KodeverkRegister.getKodeverk(s, v) as? IdType
            ?: throw IllegalArgumentException("Expected kodeverk of a valid IdType, but got ($s, $v, $dn)")
}
