package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.UUID
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
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.move.common.dokumentpakking.domain.Document
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.kith.xmlstds.msghead._2006_05_24.RefDoc
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource

private const val LEGEKONTOR_HER_ID = 2000001
private const val FASTLEGE_HER_ID = 2000002
private const val KOMMUNE_HER_ID = 1000001
private const val KOMMUNE_TJENESTE_HER_ID = 1000002
private const val PASIENT_FNR = "16822449879"

private const val PDF_DESCRIPTION = "Epikrise"
private const val PDF_ISSUE_DATE = "2026-10-14T12:00:00Z"

private val dialogmeldingXml = readResource("/dialogmelding/dialogmelding.xml").decodeToString()
private val testPdf = readResource("/dialogmelding/test.pdf")

private val legekontorAddress =
    Address(
        type = Adressetype.BESOKSADRESSE,
        streetAdr = "Storgata 1",
        postalCode = "0001",
        city = "Oslo",
        postbox = null,
        county = County("0301", "Oslo"),
        country = Country("NO", "Norge"),
    )

private val sender =
    Sender(
        parent =
            OrganizationCommunicationParty(
                ids = listOf(OrganizationId(LEGEKONTOR_HER_ID.toString(), OrganizationIdType.HER_ID)),
                address = legekontorAddress,
                name = "Legekontoret",
            ),
        child =
            PersonCommunicationParty(
                ids = listOf(PersonId(FASTLEGE_HER_ID.toString(), PersonIdType.HER_ID)),
                address = null,
                firstName = "Lege",
                middleName = "Mellom",
                lastName = "Legesen",
            ),
    )

private val receiver =
    Receiver(
        parent =
            OrganizationCommunicationParty(
                ids = listOf(OrganizationId(KOMMUNE_HER_ID.toString(), OrganizationIdType.HER_ID)),
                address = null,
                name = "Test kommune",
            ),
        child =
            OrganizationCommunicationParty(
                ids = listOf(OrganizationId(KOMMUNE_TJENESTE_HER_ID.toString(), OrganizationIdType.HER_ID)),
                address = null,
                name = "Helse og omsorg",
            ),
        patient = Patient(PASIENT_FNR, "Kari", null, "Nordmann"),
    )

private fun pdf(filename: String, resource: Resource = ByteArrayResource(testPdf)) =
    Document.builder().filename(filename).resource(resource).mimeType("application/pdf").build()

private fun sendMessageInput(
    id: UUID = UUID.randomUUID(),
    vedlegg: List<Document> = emptyList(),
    metadataFiler: Map<String, AttachmentMetadata?>? = null,
    conversationRef: ConversationRef? = null,
) =
    SendMessageInput(
        id = id,
        sender = sender,
        receiver = receiver,
        dialogmelding = DialogmeldingDeserializer.deserializeDialogmelding(dialogmeldingXml),
        vedlegg = vedlegg,
        metadataFiler = metadataFiler,
        conversationRef = conversationRef,
    )

class IncomingBusinessDocumentMapperTest :
    DescribeSpec({
        describe("MsgHead") {
            val msgId = UUID.randomUUID()
            val convId = UUID.randomUUID()
            val parentMsgId = UUID.randomUUID()
            val msgHeadXml =
                BusinessDocumentSerializer.serializeNhnMessage(
                    sendMessageInput(
                        id = msgId,
                        vedlegg = listOf(pdf("epikrise.pdf"), pdf("uten-metadata.pdf")),
                        metadataFiler = mapOf("epikrise.pdf" to AttachmentMetadata(PDF_ISSUE_DATE, PDF_DESCRIPTION)),
                        conversationRef = ConversationRef(parentMsgId.toString(), convId.toString()),
                    )
                )

            it("round trips sender, receiver and patient") {
                val msgHead = BusinessDocumentDeserializer.deserializeMsgHead(msgHeadXml)

                with(msgHead.getSender()) {
                    parent.name shouldBe "Legekontoret"
                    parent.ids shouldBe sender.parent.ids
                    parent.address shouldBe legekontorAddress
                    with(child.shouldBeInstanceOf<PersonCommunicationParty>()) {
                        ids shouldBe listOf(PersonId(FASTLEGE_HER_ID.toString(), PersonIdType.HER_ID))
                        firstName shouldBe "Lege"
                        middleName shouldBe "Mellom"
                        lastName shouldBe "Legesen"
                        // A healthcare professional is given the address of its parent organization
                        address shouldBe legekontorAddress
                    }
                }

                with(msgHead.getReceiver()) {
                    parent.address.shouldBeNull()
                    child.shouldBeInstanceOf<OrganizationCommunicationParty>().name shouldBe "Helse og omsorg"
                    patient shouldBe receiver.patient
                }
            }

            it("round trips the conversation reference") {
                val conversationRef =
                    BusinessDocumentDeserializer.deserializeMsgHead(msgHeadXml).getConversationRef().shouldNotBeNull()

                conversationRef.refToParent shouldBe parentMsgId.toString()
                conversationRef.refToConversation shouldBe convId.toString()
            }

            it("has no conversation reference when none was given") {
                val xml = BusinessDocumentSerializer.serializeNhnMessage(sendMessageInput())

                BusinessDocumentDeserializer.deserializeMsgHead(xml).getConversationRef().shouldBeNull()
            }

            it("uses the metadata of attachments when present, otherwise the filename") {
                val attachments = BusinessDocumentDeserializer.deserializeMsgHead(msgHeadXml).getAttachments()

                attachments shouldHaveSize 2
                with(attachments[0]) {
                    description shouldBe PDF_DESCRIPTION
                    issueDate.toString() shouldBe PDF_ISSUE_DATE
                    mimeType shouldBe "application/pdf"
                    data.readAllBytes() shouldBe testPdf
                }
                attachments[1].description shouldBe "uten-metadata.pdf"
                attachments[1].issueDate.shouldNotBeNull()
            }

            it("maps to a dialogmelding business document") {
                val response = IncomingBusinessDocumentMapper.toBusinessDocumentResponse(msgHeadXml)

                with(response.sbd) {
                    type shouldBe BusinessMessageType.DIALOGMELDING.type
                    messageId shouldBe msgId.toString()
                    senderHerId shouldBe FASTLEGE_HER_ID
                    receiverHerId shouldBe KOMMUNE_TJENESTE_HER_ID
                    conversationId shouldBe convId.toString()
                    parentId shouldBe parentMsgId.toString()
                    dialogmelding.metadataFiler.shouldNotBeNull().keys shouldContainExactly
                        setOf(
                            AttachmentNames.vedlegg(0, "application/pdf"),
                            AttachmentNames.vedlegg(1, "application/pdf"),
                        )
                }
                response.attachments.map { it.filename } shouldContainExactly
                    listOf(
                        AttachmentNames.DIALOGMELDING,
                        AttachmentNames.vedlegg(0, "application/pdf"),
                        AttachmentNames.vedlegg(1, "application/pdf"),
                    )
            }

            it("has no conversation scopes without a conversation reference") {
                val xml = BusinessDocumentSerializer.serializeNhnMessage(sendMessageInput())

                IncomingBusinessDocumentMapper.toBusinessDocumentResponse(xml).sbd.scopes.shouldBeEmpty()
            }

            it("rejects another MIGversion") {
                val xml = msgHeadXml.replace(BusinessDocumentDeserializer.MSG_HEAD_VERSION, "v1.1 2005-01-01")

                shouldThrow<ApplicationReceiptException> { BusinessDocumentDeserializer.deserializeMsgHead(xml) }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT
            }

            it("rejects XML that is not valid according to the schema") {
                val xml = msgHeadXml.replace(Regex("<([a-zA-Z0-9]+:)?MsgId>"), "<$1Unknown/><$1MsgId>")

                shouldThrow<ApplicationReceiptException> { BusinessDocumentDeserializer.deserializeMsgHead(xml) }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
            }

            it("rejects another root element") {
                shouldThrow<ApplicationReceiptException> {
                        BusinessDocumentDeserializer.deserializeMsgHead("<Unknown/>")
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT
            }
        }

        describe("getDialogmelding") {
            it("rejects a MsgHead without documents") {
                shouldThrow<IllegalArgumentException> { MsgHead().getDialogmelding() }.message shouldBe
                    "Found no Dialogmelding in MsgHead"
            }

            it("rejects an unsupported message type") {
                val msgHead =
                    MsgHead().apply {
                        document =
                            listOf(
                                no.kith.xmlstds.msghead._2006_05_24.Document().apply {
                                    refDoc =
                                        RefDoc().apply { content = RefDoc.Content().apply { any = listOf("text") } }
                                }
                            )
                    }

                shouldThrow<IllegalArgumentException> { msgHead.getDialogmelding() }.message shouldContain
                    "Unsupported message type"
            }
        }

        describe("BusinessDocumentSerializer") {
            it("wraps serialization errors in an application receipt error") {
                val brokenResource = mockk<Resource>()
                every { brokenResource.inputStream } throws IOException("Broken")

                shouldThrow<ApplicationReceiptException> {
                        BusinessDocumentSerializer.serializeNhnMessage(
                            sendMessageInput(vedlegg = listOf(pdf("broken.pdf", brokenResource)))
                        )
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
            }
        }

        describe("AppRec") {
            val appRecId = UUID.randomUUID()
            val originalMessageId = UUID.randomUUID()

            fun appRec(
                senderHcp: String = """<Inst><Name>Legekontor</Name><Id>$LEGEKONTOR_HER_ID</Id></Inst>""",
                receiverHcp: String = """<Inst><Name>Kommune</Name><Id>$KOMMUNE_HER_ID</Id></Inst>""",
                errors: String = "",
                status: String = """<Status V="1" DN="OK"/>""",
                namespace: String = "http://www.kith.no/xmlstds/apprec/2012-02-15",
                migVersion: String = "v1.1 2012-02-15",
            ) =
                """
                <AppRec xmlns="$namespace">
                  <MsgType V="APPREC"/>
                  <MIGversion>$migVersion</MIGversion>
                  <GenDate>2026-10-14T12:00:00</GenDate>
                  <Id>$appRecId</Id>
                  <Sender><HCP>$senderHcp</HCP></Sender>
                  <Receiver><HCP>$receiverHcp</HCP></Receiver>
                  $status
                  $errors
                  <OriginalMsgId>
                    <MsgType V="DIALOG_NOTAT"/>
                    <IssueDate>2026-10-14T11:00:00</IssueDate>
                    <Id>$originalMessageId</Id>
                  </OriginalMsgId>
                </AppRec>
                """
                    .trimIndent()

            it("uses the HER-id of a healthcare professional and of a person within an institution") {
                val xml =
                    appRec(
                        senderHcp = """<HCProf><Name>Lege Legesen</Name><Id>$FASTLEGE_HER_ID</Id></HCProf>""",
                        receiverHcp =
                            """
                            <Inst>
                              <Name>Kommune</Name>
                              <Id>$KOMMUNE_HER_ID</Id>
                              <HCPerson><Name>Saksbehandler</Name><Id>$KOMMUNE_TJENESTE_HER_ID</Id></HCPerson>
                            </Inst>
                            """,
                        errors = """<Error V="E10" DN="Ugyldig fødselsnummer" S="2.16.578.1.12.4.1.1.8221"/>""",
                    )

                val response = IncomingBusinessDocumentMapper.toBusinessDocumentResponse(xml)

                response.attachments.shouldBeEmpty()
                with(response.sbd) {
                    type shouldBe BusinessMessageType.DIALOGMELDING_KVITTERING.type
                    messageId shouldBe appRecId.toString()
                    conversationId shouldBe appRecId.toString()
                    senderHerId shouldBe FASTLEGE_HER_ID
                    receiverHerId shouldBe KOMMUNE_TJENESTE_HER_ID
                    with(dialogmeldingKvittering) {
                        relatedToMessageId shouldBe originalMessageId.toString()
                        status shouldBe DialogmeldingKvitteringStatus.OK
                        messages shouldBe listOf(KvitteringStatusMessage("E10", "Ugyldig fødselsnummer"))
                        rawReceipt shouldBe xml
                    }
                }
            }

            it("uses the HER-id of the institution when no person or department is given") {
                with(IncomingBusinessDocumentMapper.toBusinessDocumentResponse(appRec()).sbd) {
                    senderHerId shouldBe LEGEKONTOR_HER_ID
                    receiverHerId shouldBe KOMMUNE_HER_ID
                    dialogmeldingKvittering.messages.shouldNotBeNull().shouldBeEmpty()
                }
            }

            // Status codes and display names from kodeverk 8222, as sent by real systems
            withData(
                nameFn = { (status, _) -> "maps status $status" },
                """<Status V="1" DN="OK"/>""" to DialogmeldingKvitteringStatus.OK,
                """<Status V="2" DN="Avvist"/>""" to DialogmeldingKvitteringStatus.REJECTED,
                """<Status V="3" DN="OK, feil i delmelding"/>""" to
                    DialogmeldingKvitteringStatus.OK_ERROR_IN_MESSAGE_PART,
            ) { (status, expected) ->
                IncomingBusinessDocumentMapper.toBusinessDocumentResponse(appRec(status = status))
                    .sbd
                    .dialogmeldingKvittering
                    .status shouldBe expected
            }

            it("maps the status of an AppRec v1.0") {
                val xml =
                    appRec(
                        status = """<Status V="2" DN="Avvist"/>""",
                        namespace = "http://www.kith.no/xmlstds/apprec/2004-11-21",
                        migVersion = "1.0 2004-11-21",
                    )

                IncomingBusinessDocumentMapper.toBusinessDocumentResponse(xml)
                    .sbd
                    .dialogmeldingKvittering
                    .status shouldBe DialogmeldingKvitteringStatus.REJECTED
            }

            it("rejects an unknown status code") {
                shouldThrow<ApplicationReceiptException> {
                        IncomingBusinessDocumentMapper.toBusinessDocumentResponse(
                            appRec(status = """<Status V="9" DN="OK"/>""")
                        )
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.VALIDERINGSFEIL_XML
            }
        }

        describe("toBusinessDocumentResponse") {
            it("rejects unsupported root elements") {
                shouldThrow<ApplicationReceiptException> {
                        IncomingBusinessDocumentMapper.toBusinessDocumentResponse("<Unknown/>")
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT
            }
        }
    })

private fun readResource(path: String): ByteArray =
    checkNotNull(IncomingBusinessDocumentMapperTest::class.java.getResourceAsStream(path)) { "Missing $path" }
        .use { it.readAllBytes() }
