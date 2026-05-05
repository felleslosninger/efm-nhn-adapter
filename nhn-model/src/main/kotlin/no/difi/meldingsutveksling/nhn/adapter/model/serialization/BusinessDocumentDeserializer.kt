package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.TimeZone
import javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.transform.stream.StreamSource
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.model.BusinessDocumentResponse
import no.kith.xmlstds.base64container.Base64Container
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding as NhnDialogmelding1_1
import no.kith.xmlstds.msghead._2006_05_24.CS
import no.kith.xmlstds.msghead._2006_05_24.CV
import no.kith.xmlstds.msghead._2006_05_24.Ident
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.kith.xmlstds.msghead._2006_05_24.Organisation as NhnOrganisation
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.IdType
import no.ks.fiks.hdir.KodeverkRegister
import no.ks.fiks.hdir.MeldingensFunksjon
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.hdir.TypeDokumentreferanse
import no.ks.fiks.nhn.DEFAULT_ZONE
import no.ks.fiks.nhn.edi.XmlContext
import no.ks.fiks.nhn.msh.Address
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.Country
import no.ks.fiks.nhn.msh.County
import no.ks.fiks.nhn.msh.IncomingVedlegg
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonCommunicationParty
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender
import no.ks.fiks.nhn.parseOffsetDateTimeOrNull
import org.w3c.dom.Node

object BusinessDocumentDeserializer {

    private const val MSG_HEAD_ROOT = "MsgHead"
    private const val APP_REC_ROOT = "AppRec"

    private const val MSG_HEAD_VERSION = "v1.2 2006-05-24"

    private const val APPREC_VERSION_1_0 = "1.0 2004-11-21"
    private const val APPREC_VERSION_1_1 = "v1.1 2012-02-15"

    private val log = KotlinLogging.logger {}
    private val factory = XMLInputFactory.newInstance()

    @JvmStatic
    fun deserializeDialogmelding(xml: String): NhnDialogmelding1_1 {
        val dialogmelding =
            XmlContext.createUnmarshaller()
                .unmarshal(StreamSource(StringReader(xml)), NhnDialogmelding1_1::class.java)
                .value
        XmlContext.validateObject(dialogmelding)
        return dialogmelding
    }

    fun deserializeMsgHead(xml: String): BusinessDocumentResponse {
        validateRootElement(xml, MSG_HEAD_ROOT)
        if (getVersion(xml) != MSG_HEAD_VERSION) {
            throw IllegalArgumentException("Invalid MIGversion. Only $MSG_HEAD_VERSION is supported.")
        }
        XmlContext.validateXml(xml)
        val msgHead =
            XmlContext.createUnmarshaller().unmarshal(StreamSource(StringReader(xml)), MsgHead::class.java).value
        if (msgHead.msgInfo == null) {
            throw IllegalArgumentException(
                "Could not find MsgInfo in the provided XML. The message is invalid or of wrong type."
            )
        }
        return BusinessDocumentResponse(
            id = msgHead.msgInfo.msgId,
            type = msgHead.msgInfo.type.v,
            sender = msgHead.getSender(),
            receiver = msgHead.getReceiver(),
            dialogmelding = msgHead.getDialogmelding(),
            vedlegg = msgHead.getVedlegg(),
            conversationRef = msgHead.getConversationRef(),
        )
    }

    private fun validateRootElement(xml: String, expectedRoot: String) {
        getRootElement(xml).also {
            if (it != expectedRoot) {
                throw IllegalArgumentException("Expected $expectedRoot as root element, but found $it")
            }
        }
    }

    private fun getAppRecVersion(appRecXml: String) =
        getVersion(appRecXml).let { version ->
            when (version) {
                APPREC_VERSION_1_0 -> AppRecVersion.V1_0
                APPREC_VERSION_1_1 -> AppRecVersion.V1_1
                null -> throw IllegalArgumentException("Could not find MIGversion in XML")
                else -> throw IllegalArgumentException("Unknown version for AppRec: $version")
            }
        }

    private fun getVersion(xml: String): String? {
        val reader = factory.createXMLStreamReader(StringReader(xml))

        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                if (reader.localName == "MIGversion") {
                    reader.next()
                    return reader.text
                }
            }
        }
        return null
    }

    private fun getRootElement(xml: String): String? {
        val reader = factory.createXMLStreamReader(StringReader(xml))

        var iterations = 0
        while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT && iterations < 100) {
            iterations++
        }
        return reader.localName
    }

    private fun MsgHead.getType() = msgInfo.type.toMeldingensFunksjon()

    private fun MsgHead.getSender() =
        with(msgInfo.sender.organisation) { Sender(parent = getParent(), child = getChild()) }

    private fun MsgHead.getReceiver() =
        with(msgInfo.receiver.organisation) {
            Receiver(parent = getParent(), child = getChild(), patient = getPatient())
        }

    private fun NhnOrganisation.getParent() =
        OrganizationCommunicationParty(
            ids = ident.getOrganisasjonId(),
            address = convertAddress(),
            name = organisationName,
        )

    private fun NhnOrganisation.getChild() =
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

    private fun NhnOrganisation.convertAddress() =
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

    private fun MsgHead.getDialogmelding(): NhnDialogmelding1_1 =
        document.firstOrNull()?.refDoc?.content?.any?.singleOrNull()?.let {
            when (it) {
                //                    is NhnDialogmelding1_0 -> it.convert()
                is NhnDialogmelding1_1 -> it
                else -> throw IllegalArgumentException("Unsupported message type: $it")
            }
        }!!

    //    private fun NhnDialogmelding1_0.convert() = NhnDialogmelding1_1(
    //        foresporsel = readForesporsel(),
    //        notat = readNotat(),
    //    )
    //
    //    private fun NhnDialogmelding1_0.readForesporsel(): Foresporsel1_1? =
    //        foresporsel
    //            ?.singleOrNull()
    //            ?.let { foresporsel ->
    //                Foresporsel(
    //                    type = TypeOpplysningPasientsamhandlingPleieOgOmsorg.entries.firstOrNull {
    // it.verdi == foresporsel.typeForesp.v }
    //                        ?: throw IllegalArgumentException("Unknown type for typeForesp:
    // ${foresporsel.typeForesp.v}, ${foresporsel.typeForesp.dn}, ${foresporsel.typeForesp.s},
    // ${foresporsel.typeForesp.ot}"),
    //                    sporsmal = foresporsel.sporsmal,
    //                )
    //            }
    //
    //    private fun NhnDialogmelding1_0.readNotat(): Notat? =
    //        notat
    //            ?.singleOrNull()
    //            ?.let { notat ->
    //                Notat(
    //                    tema = KodeverkRegister.getKodeverk(notat.temaKodet.s, notat.temaKodet.v),
    //                    temaBeskrivelse = notat.tema,
    //                    innhold = notat.tekstNotatInnhold.getText(),
    //                    dato = notat.datoNotat?.toLocalDate(),
    //                )
    //            }
    //
    //    private fun NhnDialogmelding1_1.readForesporsel(): Foresporsel? =
    //        foresporsel
    //            ?.singleOrNull()
    //            ?.let { foresporsel ->
    //                Foresporsel(
    //                    type = TypeOpplysningPasientsamhandlingPleieOgOmsorg.entries.firstOrNull {
    // it.verdi == foresporsel.typeForesp.v }
    //                        ?: throw IllegalArgumentException("Unknown type for typeForesp:
    // ${foresporsel.typeForesp.v}, ${foresporsel.typeForesp.dn}, ${foresporsel.typeForesp.s},
    // ${foresporsel.typeForesp.ot}"),
    //                    sporsmal = foresporsel.sporsmal as? String,
    //                )
    //            }
    //
    //    private fun NhnDialogmelding1_1.readNotat(): Notat? =
    //        notat
    //            ?.singleOrNull()
    //            ?.let { notat ->
    //                Notat(
    //                    tema = KodeverkRegister.getKodeverk(notat.temaKodet.s, notat.temaKodet.v),
    //                    temaBeskrivelse = notat.tema,
    //                    innhold = notat.tekstNotatInnhold.getText(),
    //                    dato = notat.datoNotat?.toLocalDate(),
    //                )
    //            }

    private fun Any?.getText() = (this as? Node)?.firstChild?.nodeValue

    private fun XMLGregorianCalendar.toLocalDate() = toZonedDateTime().withZoneSameInstant(DEFAULT_ZONE).toLocalDate()

    private fun XMLGregorianCalendar.toOffsetDateTime() = toZonedDateTime().toOffsetDateTime()

    private fun XMLGregorianCalendar.toZonedDateTime() = toGregorianCalendarWithZone().toZonedDateTime()

    private fun XMLGregorianCalendar.toGregorianCalendarWithZone() =
        if (timezone == FIELD_UNDEFINED) {
            toGregorianCalendar(TimeZone.getTimeZone(DEFAULT_ZONE), null, null)
        } else {
            toGregorianCalendar()
        }

    private fun MsgHead.getVedlegg() =
        document.drop(1).mapNotNull { doc ->
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
                        IncomingVedlegg(
                            date = refDoc.issueDate.v?.parseOffsetDateTimeOrNull(),
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

    private fun MsgHead.getConversationRef() =
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

    private fun CS.toMeldingensFunksjon() =
        MeldingensFunksjon.entries.firstOrNull { it.verdi == v }
            ?: throw IllegalArgumentException("Unknown message type: $v, $dn")

    private fun CS.toTypeDokumentreferanse() = TypeDokumentreferanse.entries.firstOrNull { it.verdi == v }

    private fun NhnOrganisation.getId() = ident.getId()

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

private enum class AppRecVersion {
    V1_0,
    V1_1,
}
