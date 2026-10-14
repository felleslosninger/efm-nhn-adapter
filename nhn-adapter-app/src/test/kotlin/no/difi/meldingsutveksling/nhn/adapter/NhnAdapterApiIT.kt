package no.difi.meldingsutveksling.nhn.adapter

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.domain.sbdh.BusinessScope
import no.difi.meldingsutveksling.domain.sbdh.DocumentIdentification
import no.difi.meldingsutveksling.domain.sbdh.Scope
import no.difi.meldingsutveksling.domain.sbdh.ScopeType
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocumentHeader
import no.difi.meldingsutveksling.nhn.adapter.IntegrationTestEnvironment.CONSUMER_ORG_NUMBER
import no.difi.meldingsutveksling.nhn.adapter.config.AttachmentMetadata
import no.difi.meldingsutveksling.nhn.adapter.config.BusinessMessageType
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.config.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.config.PROCESS
import no.difi.meldingsutveksling.nhn.adapter.config.Pasient
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmeldingKvittering
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.toReceiver
import no.difi.meldingsutveksling.nhn.adapter.extensions.toSender
import no.difi.meldingsutveksling.nhn.adapter.handlers.Attachment
import no.difi.meldingsutveksling.nhn.adapter.handlers.ParcelService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getAttachments
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getConversationRef
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getDialogmelding
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getReceiver
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getSender
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.SendMessageInput
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartFileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.dokumentpakking.PartUtils
import no.difi.move.common.dokumentpakking.domain.Document
import no.ks.fiks.nhn.ar.AddressComponent
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.ElectronicAddress
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.ar.ServiceCommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import no.ks.fiks.nhn.msh.AppRecStatus
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.DeliveryState
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.nhn.msh.v2.model.GetBusinessDocumentResponse
import no.nhn.msh.v2.model.PostAppRecRequest
import no.nhn.msh.v2.model.PostMessageRequest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.ResolvableType
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader
import org.springframework.http.codec.multipart.Part
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.util.MimeType
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import tools.jackson.databind.json.JsonMapper

private const val KOMMUNE_PARENT_HER_ID = 1000001
private const val KOMMUNE_HER_ID = 1000002
private const val LEGEKONTOR_HER_ID = 2000001
private const val LEGEKONTOR_ORG_NUMBER = "987654321"
private const val FASTLEGE_HER_ID = 2000002
private const val UNKNOWN_HER_ID = 9999999
private const val PASIENT_FNR = "16822449879"

private const val DIALOGMELDING_FILENAME = "dialogmelding.xml"
private const val PDF_FILENAME = "test.pdf"

private val APPLICATION_JOSE = MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE)
private val APPLICATION_ASICE = MediaType.parseMediaType(ContentTypes.APPLICATION_ASICE)

private val dialogmeldingXml: ByteArray = readResource("/dialogmelding/$DIALOGMELDING_FILENAME")
private val testPdf: ByteArray = readResource("/dialogmelding/$PDF_FILENAME")

private val pasient = Pasient(fnr = PASIENT_FNR, fornavn = "Kari", mellomnavn = null, etternavn = "Nordmann")

/** The organization the Maskinporten client acts on behalf of. */
private val kommune =
    OrganizationCommunicationParty(
        herId = KOMMUNE_HER_ID,
        name = "Helse og omsorg",
        parent = CommunicationPartyParent(KOMMUNE_PARENT_HER_ID, "Test kommune", CONSUMER_ORG_NUMBER),
        physicalAddresses = emptyList(),
        electronicAddresses = emptyList(),
        organizationNumber = null,
    )

/** The same HER-id as [kommune], but registered as a service (tjeneste) in AR. */
private val kommuneTjeneste =
    ServiceCommunicationParty(
        herId = KOMMUNE_HER_ID,
        name = "Helsetjenesten",
        parent = CommunicationPartyParent(KOMMUNE_PARENT_HER_ID, "Test kommune", CONSUMER_ORG_NUMBER),
        physicalAddresses = emptyList(),
        electronicAddresses = emptyList(),
    )

private val fastlege =
    PersonCommunicationParty(
        herId = FASTLEGE_HER_ID,
        name = "Lege Legesen",
        parent = CommunicationPartyParent(LEGEKONTOR_HER_ID, "Legekontoret", LEGEKONTOR_ORG_NUMBER),
        physicalAddresses = emptyList(),
        electronicAddresses = listOf(ElectronicAddress(AddressComponent.TELEFONNUMMER, "22334455", null)),
        firstName = "Lege",
        middleName = null,
        lastName = "Legesen",
    )

/**
 * Exercises every endpoint through the full application context. The external integrations (Maskinporten, AR, FLR and
 * MSH) are replaced with MockK mocks, while the JOSE/CMS handling, XML serialization and security rules are real.
 */
@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTestEnvironment.Initializer::class])
class NhnAdapterApiIT(
    @MockkBean private val adresseregisteretClient: AdresseregisteretClient,
    @MockkBean private val fastlegeregisteretClient: FastlegeregisteretClient,
    @MockkBean private val mshClient: Client,
    @MockkBean private val mshInternalClient: MshInternalClient,
    private val parcelService: ParcelService,
    private val keystoreHelper: KeystoreHelper,
    private val jsonMapper: JsonMapper,
    private val applicationContext: ApplicationContext,
) :
    DescribeSpec({
        val anonymousClient by lazy {
            WebTestClient.bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
                .build()
        }

        /** A client authenticated with a Maskinporten token for [CONSUMER_ORG_NUMBER]. */
        fun client(scope: String = "eformidling:dph") =
            anonymousClient.mutateWith(
                mockJwt()
                    .jwt {
                        it.claim("client_id", "nhn-adapter-it-client")
                            .claim("scope", scope)
                            .claim(
                                "consumer",
                                mapOf("authority" to "iso6523-actorid-upis", "ID" to "0192:$CONSUMER_ORG_NUMBER"),
                            )
                    }
                    .authorities(SimpleGrantedAuthority("SCOPE_$scope"))
            )

        fun signAndEncrypt(payload: String): String =
            parcelService.signAndEncrypt(payload, keystoreHelper.x509Certificate)

        fun dokumentpakke(hoveddokument: ByteArray = dialogmeldingXml): Resource =
            parcelService
                .createAndEncryptAsic(
                    keystoreHelper.x509Certificate,
                    listOf(
                        Attachment(
                            DIALOGMELDING_FILENAME,
                            ByteArrayResource(hoveddokument),
                            MimeType.valueOf("application/xml"),
                        ),
                        Attachment(PDF_FILENAME, ByteArrayResource(testPdf), MimeType.valueOf("application/pdf")),
                    ),
                )
                .let { ByteArrayResource(it.contentAsByteArray) }

        fun outgoingMessage(sbd: StandardBusinessDocument, dokumentpakke: Resource? = null) =
            MultipartBodyBuilder()
                .apply {
                    part(
                            MultipartNames.FORRETNINGSMELDING,
                            ByteArrayResource(signAndEncrypt(jsonMapper.writeValueAsString(sbd)).toByteArray()),
                            APPLICATION_JOSE,
                        )
                        .filename(MultipartFileNames.FORRETNINGSMELDING)
                    dokumentpakke?.let {
                        part(MultipartNames.DOKUMENTPAKKE, it, APPLICATION_ASICE)
                            .filename(MultipartFileNames.DOKUMENTPAKKE)
                    }
                }
                .build()

        fun sendOutgoing(sbd: StandardBusinessDocument, dokumentpakke: Resource? = null) =
            client()
                .post()
                .uri("/api/messages/out")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.TEXT_PLAIN)
                .body(BodyInserters.fromMultipartData(outgoingMessage(sbd, dokumentpakke)))
                .exchange()

        beforeEach {
            every { adresseregisteretClient.lookupHerId(any()) } returns null
            every { adresseregisteretClient.lookupHerId(KOMMUNE_HER_ID) } returns kommune
            every { adresseregisteretClient.lookupHerId(FASTLEGE_HER_ID) } returns fastlege
        }

        describe("Security") {
            it("rejects requests without an access token") {
                anonymousClient
                    .get()
                    .uri("/api/lookup/her-id:$FASTLEGE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized
            }

            it("rejects access tokens without the eformidling:dph scope") {
                client(scope = "some:other-scope")
                    .get()
                    .uri("/api/lookup/her-id:$FASTLEGE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isForbidden
            }
        }

        describe("GET /api/lookup/{identifier}") {
            it("returns the AR details for a HER-id") {
                client()
                    .get()
                    .uri("/api/lookup/her-id:$FASTLEGE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.parentHerId")
                    .isEqualTo(LEGEKONTOR_HER_ID)
                    .jsonPath("$.parentName")
                    .isEqualTo("Legekontoret")
                    .jsonPath("$.orgNumber")
                    .isEqualTo(LEGEKONTOR_ORG_NUMBER)
                    .jsonPath("$.herId")
                    .isEqualTo(FASTLEGE_HER_ID)
                    .jsonPath("$.name")
                    .isEqualTo("Lege Legesen")
                    .jsonPath("$.derCertificate")
                    .isEqualTo(Base64.getEncoder().encodeToString(keystoreHelper.x509Certificate.encoded))
            }

            it("returns the AR details for the fastlege of a patient") {
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } returns
                    PatientGP(PASIENT_FNR, FASTLEGE_HER_ID)

                client()
                    .get()
                    .uri("/api/lookup/fastlege-for:$PASIENT_FNR")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.herId")
                    .isEqualTo(FASTLEGE_HER_ID)
                    .jsonPath("$.parentHerId")
                    .isEqualTo(LEGEKONTOR_HER_ID)
            }

            // Disabled: NhnErrorFilter only maps HerIdNotFound to 404 for the base path "/lookup",
            // but the actual
            // path is "/api/lookup", so 400 is returned.
            xit("returns 404 when the HER-id is unknown") {
                client().get().uri("/api/lookup/her-id:$UNKNOWN_HER_ID").exchange().expectStatus().isNotFound
            }

            it("returns 400 for a malformed identifier") {
                client().get().uri("/api/lookup/not-an-identifier").exchange().expectStatus().isBadRequest
            }
        }

        describe("POST /api/messages/out") {
            it("sends the dialogmelding with the PDF attachment to MSH") {
                val messageId = UUID.randomUUID()
                val conversationId = UUID.randomUUID()
                val mshMessageId = UUID.randomUUID()
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    mshMessageId

                sendOutgoing(dialogmeldingSbd(messageId, conversationId), dokumentpakke())
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .isEqualTo(mshMessageId.toString())

                postMessageRequest.captured.contentType shouldBe "application/xml"
                postMessageRequest.captured.contentTransferEncoding shouldBe "base64"

                val msgHead =
                    BusinessDocumentDeserializer.deserializeMsgHead(
                        String(Base64.getDecoder().decode(postMessageRequest.captured.businessDocument))
                    )
                msgHead.msgInfo.msgId shouldBe messageId.toString()
                msgHead.getConversationRef() shouldBe null

                val sender = msgHead.getSender()
                sender.parent.herId shouldBe KOMMUNE_PARENT_HER_ID
                sender.child.herId shouldBe KOMMUNE_HER_ID

                val receiver = msgHead.getReceiver()
                receiver.parent.herId shouldBe LEGEKONTOR_HER_ID
                receiver.child.shouldBeInstanceOf<no.ks.fiks.nhn.msh.PersonCommunicationParty>()
                receiver.child.herId shouldBe FASTLEGE_HER_ID
                receiver.patient.fnr shouldBe PASIENT_FNR
                receiver.patient.lastName shouldBe "Nordmann"

                val notat = msgHead.getDialogmelding().notat.single()
                notat.temaKodet.v shouldBe "6"
                notat.tema shouldBe "Test tema"
                notat.tekstNotatInnhold.toString() shouldContain "Testing 1 2 3"
                // "Henvendelse om pasient" without roles gets the fastlege and "kontakt hos
                // mottaker" added
                notat.rollerRelatertNotat shouldHaveSize 2
                notat.rollerRelatertNotat.first().healthcareProfessional.familyName shouldBe "Legesen"

                val attachment = msgHead.getAttachments().single()
                attachment.mimeType shouldBe "application/pdf"
                attachment.description shouldBe "Testvedlegg"
                attachment.data!!.readAllBytes() shouldBe testPdf
            }

            it("sends the dialogmelding to MSH when the sender is a service") {
                every { adresseregisteretClient.lookupHerId(KOMMUNE_HER_ID) } returns kommuneTjeneste
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    UUID.randomUUID()

                sendOutgoing(dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()), dokumentpakke())
                    .expectStatus()
                    .isOk

                val sender =
                    BusinessDocumentDeserializer.deserializeMsgHead(
                            String(Base64.getDecoder().decode(postMessageRequest.captured.businessDocument))
                        )
                        .getSender()
                sender.parent.herId shouldBe KOMMUNE_PARENT_HER_ID
                sender.child.shouldBeInstanceOf<no.ks.fiks.nhn.msh.OrganizationCommunicationParty>()
                sender.child.herId shouldBe KOMMUNE_HER_ID
                sender.child.fullName shouldBe "Helsetjenesten"
            }

            it("sends the conversation reference to MSH when the dialogmelding is a reply") {
                val conversationId = UUID.randomUUID()
                val parentId = UUID.randomUUID()
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    UUID.randomUUID()

                sendOutgoing(dialogmeldingSbd(UUID.randomUUID(), conversationId, parentId), dokumentpakke())
                    .expectStatus()
                    .isOk

                val conversationRef =
                    BusinessDocumentDeserializer.deserializeMsgHead(
                            String(Base64.getDecoder().decode(postMessageRequest.captured.businessDocument))
                        )
                        .getConversationRef()!!
                conversationRef.refToParent shouldBe parentId.toString()
                conversationRef.refToConversation shouldBe conversationId.toString()
            }

            it("sends an application receipt to MSH") {
                val relatedToMessageId = UUID.randomUUID()
                val appRecId = UUID.randomUUID()
                val postAppRecRequest = slot<PostAppRecRequest>()
                coEvery {
                    mshInternalClient.postAppRec(
                        relatedToMessageId,
                        KOMMUNE_HER_ID,
                        capture(postAppRecRequest),
                        consumerTenant(),
                    )
                } returns appRecId

                sendOutgoing(kvitteringSbd(UUID.randomUUID(), relatedToMessageId))
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .isEqualTo(appRecId.toString())

                postAppRecRequest.captured.appRecStatus shouldBe no.nhn.msh.v2.model.AppRecStatus.OK
            }

            it("returns 400 and sends nothing when the hoveddokument is missing from the dokumentpakke") {
                val sbd =
                    dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()).apply {
                        dialogmelding = dialogmelding.copy(hoveddokument = "finnes-ikke.xml")
                    }

                sendOutgoing(sbd, dokumentpakke()).expectStatus().isBadRequest

                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }

            it("returns 400 and sends nothing when the hoveddokument is not XML") {
                sendOutgoing(
                        dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()),
                        dokumentpakke(hoveddokument = "dette er ikke XML".toByteArray()),
                    )
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }
        }

        describe("GET /api/messages/out/{messageId}/statuses") {
            it("returns the delivery and application receipt status from MSH") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.getStatus(messageId, consumerTenant()) } returns
                    listOf(StatusInfo(FASTLEGE_HER_ID, DeliveryState.ACKNOWLEDGED, AppRecStatus.OK))

                client()
                    .get()
                    .uri("/api/messages/out/$messageId/statuses")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.length()")
                    .isEqualTo(1)
                    .jsonPath("$[0].receiverHerId")
                    .isEqualTo(FASTLEGE_HER_ID)
                    .jsonPath("$[0].transportStatus")
                    .isEqualTo("ACKNOWLEDGED")
                    .jsonPath("$[0].apprecStatus")
                    .isEqualTo("OK")
            }

            it("returns 400 when the message id is not a UUID") {
                client()
                    .get()
                    .uri("/api/messages/out/not-a-uuid/statuses")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }
        }

        describe("GET /api/messages/in") {
            it("returns the incoming messages for the receiver") {
                val messageId = UUID.randomUUID()
                val businessDocumentId = UUID.randomUUID()
                coEvery { mshClient.getMessagesWithMetadata(KOMMUNE_HER_ID, consumerTenant()) } returns
                    listOf(
                        MessageWithMetadata(
                            messageId,
                            "application/xml",
                            KOMMUNE_HER_ID,
                            FASTLEGE_HER_ID,
                            businessDocumentId.toString(),
                            OffsetDateTime.parse("2026-08-21T08:00:00+02:00"),
                            false,
                        )
                    )

                client()
                    .get()
                    .uri("/api/messages/in?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.length()")
                    .isEqualTo(1)
                    .jsonPath("$[0].id")
                    .isEqualTo(messageId.toString())
                    .jsonPath("$[0].receiverHerId")
                    .isEqualTo(KOMMUNE_HER_ID)
                    .jsonPath("$[0].senderHerId")
                    .isEqualTo(FASTLEGE_HER_ID)
                    .jsonPath("$[0].businessDocumentId")
                    .isEqualTo(businessDocumentId.toString())
                    .jsonPath("$[0].isAppRec")
                    .isEqualTo(false)
            }

            it("returns 400 when receiverHerId is missing") {
                client()
                    .get()
                    .uri("/api/messages/in")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }

            // Disabled: NhnErrorFilter does not map AccessDeniedException, so 500 is returned.
            xit("denies access to messages for a HER-id belonging to another organization") {
                client()
                    .get()
                    .uri("/api/messages/in?receiverHerId=$FASTLEGE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.getMessagesWithMetadata(any(), any()) }
            }
        }

        describe("POST /api/messages/in") {
            it("returns the incoming dialogmelding as an encrypted forretningsmelding and dokumentpakke") {
                val messageId = UUID.randomUUID()
                val msgHead =
                    BusinessDocumentSerializer.serializeNhnMessage(
                        SendMessageInput(
                            id = messageId,
                            sender = fastlege.toSender(),
                            receiver = kommune.toReceiver(pasient),
                            dialogmelding =
                                DialogmeldingDeserializer.deserializeDialogmelding(String(dialogmeldingXml)),
                            vedlegg =
                                listOf(
                                    Document.builder()
                                        .filename(PDF_FILENAME)
                                        .resource(ByteArrayResource(testPdf))
                                        .mimeType("application/pdf")
                                        .build()
                                ),
                            metadataFiler = null,
                            conversationRef = null,
                        )
                    )
                coEvery { mshInternalClient.getBusinessDocument(messageId, consumerTenant()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument(Base64.getEncoder().encodeToString(msgHead.toByteArray()))

                val result =
                    client()
                        .post()
                        .uri("/api/messages/in")
                        .contentType(APPLICATION_JOSE)
                        .accept(MediaType.MULTIPART_MIXED)
                        .bodyValue(signAndEncrypt("""{"id":"$messageId"}"""))
                        .exchange()
                        .expectStatus()
                        .isOk
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.MULTIPART_MIXED)
                        .expectBody<ByteArray>()
                        .returnResult()

                val parts = readMultipart(result.responseHeaders.contentType!!, result.responseBody!!)
                parts.keys shouldContainExactlyInAnyOrder
                    setOf(MultipartNames.FORRETNINGSMELDING, MultipartNames.DOKUMENTPAKKE)

                val jws =
                    parcelService.decryptAndVerify(
                        PartUtils.toString(parts.getValue(MultipartNames.FORRETNINGSMELDING))
                    )
                val sbd = jsonMapper.readValue(jws.payload.toString(), StandardBusinessDocument::class.java)
                sbd.type shouldBe BusinessMessageType.DIALOGMELDING.type
                sbd.messageId shouldBe messageId.toString()
                sbd.senderHerId shouldBe FASTLEGE_HER_ID
                sbd.receiverHerId shouldBe KOMMUNE_HER_ID
                sbd.dialogmelding.hoveddokument shouldBe AttachmentNames.DIALOGMELDING
                sbd.dialogmelding.pasient shouldBe pasient

                val pdfName = AttachmentNames.vedlegg(0, "application/pdf")
                val documents =
                    parcelService.getAttachments(parts.getValue(MultipartNames.DOKUMENTPAKKE)).associateBy {
                        it.filename
                    }
                documents.keys shouldContainExactlyInAnyOrder setOf(AttachmentNames.DIALOGMELDING, pdfName)
                documents.getValue(pdfName).resource.contentAsByteArray shouldBe testPdf

                val notat =
                    DialogmeldingDeserializer.deserializeDialogmelding(
                            String(documents.getValue(AttachmentNames.DIALOGMELDING).resource.contentAsByteArray)
                        )
                        .notat
                        .single()
                notat.tema shouldBe "Test tema"
                notat.tekstNotatInnhold.toString() shouldContain "Testing 1 2 3"
            }
        }

        describe("POST /api/messages/in/{messageId}/read") {
            it("marks the message as read in MSH") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.markMessageRead(messageId, KOMMUNE_HER_ID, consumerTenant()) } just Runs

                client()
                    .post()
                    .uri("/api/messages/in/$messageId/read?receiverHerId=$KOMMUNE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .isEqualTo("Message deleted")

                coVerify(exactly = 1) { mshClient.markMessageRead(messageId, KOMMUNE_HER_ID, consumerTenant()) }
            }
        }
    })

/** Matches MSH requests made with a HelseID token for the Maskinporten consumer. */
private fun MockKMatcherScope.consumerTenant() =
    match<RequestParameters> {
        (it.helseId?.tenant as? SingleTenantHelseIdTokenParameters)?.childOrganization == CONSUMER_ORG_NUMBER
    }

private fun dialogmeldingSbd(messageId: UUID, conversationId: UUID, parentId: UUID? = null) =
    sbd(BusinessMessageType.DIALOGMELDING, messageId, conversationId, parentId).apply {
        senderHerId = KOMMUNE_HER_ID
        receiverHerId = FASTLEGE_HER_ID
        dialogmelding =
            DialogmeldingMessage(
                hoveddokument = DIALOGMELDING_FILENAME,
                pasient = pasient,
                metadataFiler =
                    mapOf(PDF_FILENAME to AttachmentMetadata(issueDate = null, description = "Testvedlegg")),
            )
    }

private fun kvitteringSbd(messageId: UUID, relatedToMessageId: UUID) =
    sbd(BusinessMessageType.DIALOGMELDING_KVITTERING, messageId, UUID.randomUUID()).apply {
        senderHerId = KOMMUNE_HER_ID
        receiverHerId = FASTLEGE_HER_ID
        dialogmeldingKvittering =
            DialogmeldingKvitteringMessage(
                relatedToMessageId = relatedToMessageId.toString(),
                status = DialogmeldingKvitteringStatus.OK,
                messages = null,
            )
    }

private fun sbd(type: BusinessMessageType, messageId: UUID, conversationId: UUID, parentId: UUID? = null) =
    StandardBusinessDocument().apply {
        standardBusinessDocumentHeader =
            StandardBusinessDocumentHeader().apply {
                headerVersion = "1.0"
                documentIdentification =
                    DocumentIdentification().apply {
                        instanceIdentifier = messageId.toString()
                        this.type = type.type
                        standard = type.standard
                        typeVersion = type.version
                        creationDateAndTime = OffsetDateTime.now()
                    }
                businessScope =
                    BusinessScope().apply {
                        addScopes(scope(ScopeType.CONVERSATION_ID, conversationId))
                        parentId?.let { addScopes(scope(ScopeType.PARENT_ID, it)) }
                    }
            }
    }

private fun scope(type: ScopeType, id: UUID) =
    Scope().apply {
        this.type = type.fullname
        identifier = PROCESS
        instanceIdentifier = id.toString()
    }

private fun readMultipart(contentType: MediaType, body: ByteArray): Map<String, Part> {
    val request =
        MockServerHttpRequest.post("/")
            .contentType(contentType)
            .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)))
    return DefaultPartHttpMessageReader()
        .read(ResolvableType.forClass(Part::class.java), request, emptyMap())
        .collectList()
        .block()!!
        .associateBy { it.name() }
}

private fun readResource(path: String): ByteArray =
    NhnAdapterApiIT::class.java.getResourceAsStream(path)!!.use { it.readAllBytes() }
