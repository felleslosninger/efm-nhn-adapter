package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.xml.datatype.DatatypeFactory
import kotlin.time.Clock
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.messages.AttachmentMetadata
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.move.common.dokumentpakking.domain.Document as DokumentpakkeDocument
import no.kith.xmlstds.base64container.Base64Container
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.kith.xmlstds.msghead._2006_05_24.Address as NhnAddress
import no.kith.xmlstds.msghead._2006_05_24.CS
import no.kith.xmlstds.msghead._2006_05_24.CV
import no.kith.xmlstds.msghead._2006_05_24.ConversationRef as NhnConversationRef
import no.kith.xmlstds.msghead._2006_05_24.Document
import no.kith.xmlstds.msghead._2006_05_24.HealthcareProfessional
import no.kith.xmlstds.msghead._2006_05_24.Ident
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.kith.xmlstds.msghead._2006_05_24.MsgInfo
import no.kith.xmlstds.msghead._2006_05_24.Organisation as NhnOrganisation
import no.kith.xmlstds.msghead._2006_05_24.Patient as NhnPatient
import no.kith.xmlstds.msghead._2006_05_24.Receiver as NhnReceiver
import no.kith.xmlstds.msghead._2006_05_24.RefDoc
import no.kith.xmlstds.msghead._2006_05_24.Sender as NhnSender
import no.kith.xmlstds.msghead._2006_05_24.TS
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.IdType
import no.ks.fiks.hdir.KodeverkVerdi
import no.ks.fiks.hdir.MeldingensFunksjon
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.hdir.TypeDokumentreferanse
import no.ks.fiks.nhn.edi.XmlContext
import no.ks.fiks.nhn.msh.Address
import no.ks.fiks.nhn.msh.CommunicationParty
import no.ks.fiks.nhn.msh.DialogmeldingVersion
import no.ks.fiks.nhn.msh.Id
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.PersonCommunicationParty
import org.springframework.core.io.Resource

private const val VEDLEGG_MAX_BYTES = 18 * 1000 * 1000

object BusinessDocumentSerializer {
    fun serializeNhnMessage(businessDocument: SendMessageInput): String {
        try {
            return serialize(
                    buildMsgHead(businessDocument).apply {
                        document = buildList {
                            add(buildDialogmeldingDocument(businessDocument.dialogmelding))
                            addAll(
                                businessDocument.vedlegg.map {
                                    buildVedleggDocument(it, businessDocument.metadataFiler)
                                }
                            )
                        }
                    }
                )
                .also { XmlContext.validateXml(it) }
        } catch (e: MessageTooLargeException) {
            throw e
        } catch (e: Exception) {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                "Error while serializing Dialogmelding",
                e,
            )
        }
    }

    private fun serialize(jaxbElement: Any): String =
        StringWriter().also { XmlContext.createMarshaller().marshal(jaxbElement, it) }.toString()

    private fun buildMsgHead(businessDocument: SendMessageInput) =
        MsgHead().apply {
            msgInfo =
                MsgInfo().apply {
                    type = buildMsgInfoType(DialogmeldingVersion.V1_1)
                    miGversion = BusinessDocumentDeserializer.MSG_HEAD_VERSION
                    genDate = currentDateTime()
                    msgId = businessDocument.id.toString()
                    sender =
                        NhnSender().apply {
                            organisation =
                                toOrganisation(businessDocument.sender.parent, businessDocument.sender.child)
                        }
                    receiver =
                        NhnReceiver().apply {
                            organisation =
                                toOrganisation(businessDocument.receiver.parent, businessDocument.receiver.child)
                        }
                    patient =
                        NhnPatient().apply {
                            givenName = businessDocument.receiver.patient.firstName
                            middleName = businessDocument.receiver.patient.middleName
                            familyName = businessDocument.receiver.patient.lastName
                            ident =
                                listOf(
                                    Ident().apply {
                                        id = businessDocument.receiver.patient.fnr
                                        typeId = toCv(PersonIdType.FNR)
                                    }
                                )
                        }
                    document =
                        listOf( // Add empty doc for validation, which is overwritten later
                            Document().apply { refDoc = RefDoc().apply { msgType = CS() } }
                        )
                    conversationRef =
                        businessDocument.conversationRef?.let {
                            NhnConversationRef().apply {
                                refToParent = it.refToParent
                                refToConversation = it.refToConversation
                            }
                        }
                }
        }

    private fun toOrganisation(parent: OrganizationCommunicationParty, child: CommunicationParty): NhnOrganisation =
        toOrganisation(parent).apply {
            organisation = (child as? OrganizationCommunicationParty)?.let { toOrganisation(it) }
            healthcareProfessional = (child as? PersonCommunicationParty)?.let { toHealthcareProfessional(it) }
        }

    private fun toOrganisation(organization: OrganizationCommunicationParty): NhnOrganisation =
        NhnOrganisation().apply {
            organisationName = organization.name
            ident = organization.ids.map { toIdent(it) }
            address = organization.address?.let { convert(it) }
        }

    private fun toHealthcareProfessional(person: PersonCommunicationParty): HealthcareProfessional =
        HealthcareProfessional().apply {
            givenName = person.firstName
            middleName = person.middleName
            familyName = person.lastName
            ident = person.ids.map { toIdent(it) }
            address = person.address?.let { convert(it) }
        }

    private fun buildMsgInfoType(version: DialogmeldingVersion) =
        when (version) {
            DialogmeldingVersion.V1_0 -> MeldingensFunksjon.DIALOG_FORESPORSEL
            DialogmeldingVersion.V1_1 -> MeldingensFunksjon.DIALOG_HELSEFAGLIG
        }.toMsgHeadCS()

    private fun currentDateTime() =
        DatatypeFactory.newInstance()
            .newXMLGregorianCalendar(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            )

    private fun toIdent(input: Id) =
        Ident().apply {
            id = input.id
            typeId = toCv(input.type)
        }

    private fun toCv(type: IdType) =
        CV().apply {
            v = type.verdi
            dn = type.navn
            s = type.kodeverk
        }

    private fun convert(address: Address) =
        NhnAddress().apply {
            type = address.type?.toMsgHeadCS()
            streetAdr = address.streetAdr
            postalCode = address.postalCode
            city = address.city
            postbox = address.postbox
            county = address.county?.let { msgHeadCS(it.code, it.name) }
            country = address.country?.let { msgHeadCS(it.code, it.name) }
        }

    private fun buildDialogmeldingDocument(dialogmelding: Dialogmelding) =
        Document().apply {
            refDoc =
                RefDoc().apply {
                    msgType = TypeDokumentreferanse.XML.toMsgHeadCS()
                    content = RefDoc.Content().apply { any = listOf(dialogmelding) }
                }
        }

    private fun buildVedleggDocument(
        attachment: DokumentpakkeDocument,
        metadataFiler: Map<String, AttachmentMetadata?>?,
    ): Document {
        val metadata = metadataFiler?.get(attachment.filename)

        return Document().apply {
            refDoc =
                RefDoc().apply {
                    issueDate = TS().apply { v = metadata?.issueDate ?: Clock.System.now().toString() }
                    msgType = TypeDokumentreferanse.VEDLEGG.toMsgHeadCS()
                    mimeType = attachment.mimeType.toString()
                    description = metadata?.description ?: attachment.filename
                    content = RefDoc.Content().apply { any = listOf(buildContainer(attachment.resource)) }
                }
        }
    }

    private fun buildContainer(resource: Resource) =
        resource.inputStream.use { data ->
            Base64Container().apply {
                value =
                    data.readNBytes(VEDLEGG_MAX_BYTES).also {
                        if (it.size == VEDLEGG_MAX_BYTES && data.read() != -1) {
                            throw MessageTooLargeException(
                                "The size of vedlegg exceeds the max size of $VEDLEGG_MAX_BYTES bytes"
                            )
                        }
                    }
            }
        }
}

private fun KodeverkVerdi.toMsgHeadCS() = msgHeadCS(verdi, navn)

private fun msgHeadCS(value: String?, displayName: String?) =
    CS().apply {
        v = value
        dn = displayName
    }
