package no.difi.meldingsutveksling.nhn.adapter

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Called
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.difi.meldingsutveksling.domain.sbdh.BusinessScope
import no.difi.meldingsutveksling.domain.sbdh.DocumentIdentification
import no.difi.meldingsutveksling.domain.sbdh.Scope
import no.difi.meldingsutveksling.domain.sbdh.ScopeType
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocumentHeader
import no.difi.meldingsutveksling.nhn.adapter.IntegrationTestEnvironment.CONSUMER_ORG_NUMBER
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.extensions.dialogmeldingKvittering
import no.difi.meldingsutveksling.nhn.adapter.extensions.receiverHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.senderHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.toReceiver
import no.difi.meldingsutveksling.nhn.adapter.extensions.toSender
import no.difi.meldingsutveksling.nhn.adapter.handlers.LargeSendLimiter
import no.difi.meldingsutveksling.nhn.adapter.handlers.ParcelService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.Attachment
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getAttachments
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getConversationRef
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getDialogmelding
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getReceiver
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentDeserializer.getSender
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.BusinessDocumentSerializer
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.DOCUMENTS_MAX_BYTES
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.SendMessageInput
import no.difi.meldingsutveksling.nhn.adapter.logging.FnrMaskingLogstashEncoder
import no.difi.meldingsutveksling.nhn.adapter.logging.SpanUrlMasking
import no.difi.meldingsutveksling.nhn.adapter.messages.AttachmentMetadata
import no.difi.meldingsutveksling.nhn.adapter.messages.BusinessMessageType
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.PROCESS
import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
import no.difi.meldingsutveksling.nhn.adapter.model.AttachmentNames
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartFileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.dokumentpakking.JsonWebEncryption
import no.difi.move.common.dokumentpakking.PartUtils
import no.difi.move.common.dokumentpakking.domain.Document
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.nhn.ar.rest.AddressNotFoundException
import no.ks.fiks.nhn.ar.rest.AdresseregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import no.ks.fiks.nhn.msh.AppRecInfo
import no.ks.fiks.nhn.msh.AppRecStatus
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.DeliveryState
import no.ks.fiks.nhn.msh.GetStatusResponse
import no.ks.fiks.nhn.msh.HttpServerException
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.NotificationType
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.ks.fiks.nhn.msh.UnreadNotification
import no.nhn.msh.v3.model.GetBusinessDocumentResponse
import no.nhn.msh.v3.model.GetMessageResponse as NhnMessage
import no.nhn.msh.v3.model.PostAppRecRequest
import no.nhn.msh.v3.model.PostMessageRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.reactor.netty.NettyServerCustomizer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.ResolvableType
import org.springframework.core.codec.StringDecoder
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.codec.DecoderHttpMessageReader
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader
import org.springframework.http.codec.multipart.MultipartHttpMessageWriter
import org.springframework.http.codec.multipart.Part
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.util.MimeType
import org.springframework.util.MultiValueMap
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

private const val KOMMUNE_PARENT_HER_ID = 1000001
private const val KOMMUNE_HER_ID = 1000002
private const val LEGEKONTOR_HER_ID = 2000001
private const val LEGEKONTOR_ORG_NUMBER = "987654321"

/** A supplier that acts on behalf of [CONSUMER_ORG_NUMBER]. */
private const val SUPPLIER_ORG_NUMBER = "912345678"
private const val FASTLEGE_HER_ID = 2000002
private const val LEGEKONTOR_TJENESTE_HER_ID = 2000003
private const val UNKNOWN_HER_ID = 9999999
private const val PASIENT_FNR = "16822449879"

private const val AUDIT_LOGGER = "no.idporten.logging.audit.AuditLog"

private const val DIALOGMELDING_FILENAME = "dialogmelding.xml"
private const val PDF_FILENAME = "test.pdf"

private val APPLICATION_JOSE = MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE)
private val APPLICATION_ASICE = MediaType.parseMediaType(ContentTypes.APPLICATION_ASICE)

private val dialogmeldingXml: ByteArray = readResource("/dialogmelding/$DIALOGMELDING_FILENAME")
private val testPdf: ByteArray = readResource("/dialogmelding/$PDF_FILENAME")

private val pasient = Pasient(fnr = PASIENT_FNR, fornavn = "Kari", mellomnavn = null, etternavn = "Nordmann")

private val legekontor = ArTestData.parentOrganization(LEGEKONTOR_HER_ID, "Legekontoret", LEGEKONTOR_ORG_NUMBER)

/** The organization the Maskinporten client acts on behalf of. An organization is its own parent. */
private val kommune = ArTestData.organization(KOMMUNE_HER_ID, "Helse og omsorg", CONSUMER_ORG_NUMBER)

/** The same HER-id as [kommune], but registered as a service (tjeneste) in AR. */
private val kommuneTjeneste =
    ArTestData.service(
        KOMMUNE_HER_ID,
        "Helsetjenesten",
        ArTestData.parentOrganization(KOMMUNE_PARENT_HER_ID, "Test kommune", CONSUMER_ORG_NUMBER),
    )

private val legekontorTjeneste = ArTestData.service(LEGEKONTOR_TJENESTE_HER_ID, "Legekontorets resepsjon", legekontor)

private val fastlege = ArTestData.person(FASTLEGE_HER_ID, "Lege Legesen", legekontor, phoneNumber = "22334455")

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
    private val largeSendLimiter: LargeSendLimiter,
    private val multipartTempFileSweeper: MultipartTempFileSweeper,
    private val keystoreHelper: KeystoreHelper,
    private val jsonMapper: JsonMapper,
    private val applicationContext: ApplicationContext,
    private val serverCodecConfigurer: ServerCodecConfigurer,
) :
    DescribeSpec({
        val anonymousClient by lazy {
            WebTestClient.bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .configureClient()
                .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
                .build()
        }

        /**
         * A client authenticated with a Maskinporten token for [CONSUMER_ORG_NUMBER], issued to [supplier] when it
         * acts for the consumer through [delegationSource].
         */
        fun client(scope: String = "eformidling:dph", supplier: String? = null, delegationSource: String? = null) =
            anonymousClient.mutateWith(
                mockJwt()
                    .jwt {
                        it.claim("client_id", "nhn-adapter-it-client")
                            .claim("scope", scope)
                            .claim(
                                "consumer",
                                mapOf("authority" to "iso6523-actorid-upis", "ID" to "0192:$CONSUMER_ORG_NUMBER"),
                            )
                        supplier?.let { orgNumber ->
                            it.claim(
                                "supplier",
                                mapOf("authority" to "iso6523-actorid-upis", "ID" to "0192:$orgNumber"),
                            )
                        }
                        delegationSource?.let { source -> it.claim("delegation_source", source) }
                    }
                    .authorities(SimpleGrantedAuthority("SCOPE_$scope"))
            )

        fun trustedDelegationSource(): String =
            applicationContext.environment.getRequiredProperty("security.delegation-source")

        fun signAndEncrypt(payload: String): String =
            parcelService.signAndEncrypt(payload, keystoreHelper.x509Certificate)

        /** The files in spring.webflux.multipart.file-storage-directory, where Spring stores multipart parts. */
        fun multipartTempFiles(): Set<Path> =
            Files.list(multipartTempFileSweeper.directory).use { files -> files.toList().toSet() }

        fun dokumentpakke(
            hoveddokument: ByteArray = dialogmeldingXml,
            vedlegg: List<Attachment> =
                listOf(Attachment(PDF_FILENAME, ByteArrayResource(testPdf), MimeType.valueOf("application/pdf"))),
        ): Resource =
            parcelService
                .createAndEncryptAsic(
                    keystoreHelper.x509Certificate,
                    listOf(
                        Attachment(
                            DIALOGMELDING_FILENAME,
                            ByteArrayResource(hoveddokument),
                            MimeType.valueOf("application/xml"),
                        )
                    ) + vedlegg,
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

        /** As [sendOutgoing], but encoded up front, so that the request has a Content-Length. */
        fun sendOutgoingWithContentLength(sbd: StandardBusinessDocument, dokumentpakke: Resource) =
            MockClientHttpRequest(HttpMethod.POST, "/").let { encoded ->
                MultipartHttpMessageWriter()
                    .write(
                        Mono.just(outgoingMessage(sbd, dokumentpakke)),
                        ResolvableType.forClass(MultiValueMap::class.java),
                        MediaType.MULTIPART_FORM_DATA,
                        encoded,
                        emptyMap(),
                    )
                    .block()
                val body =
                    DataBufferUtils.join(encoded.body)
                        .map { buffer -> ByteArray(buffer.readableByteCount()).also { buffer.read(it) } }
                        .block()!!
                client()
                    .post()
                    .uri("/api/messages/out")
                    .contentType(encoded.headers.contentType!!)
                    .contentLength(body.size.toLong())
                    .accept(MediaType.TEXT_PLAIN)
                    .bodyValue(body)
                    .exchange()
            }

        /** Runs [block] while other sends hold all the permits of [largeSendLimiter]. */
        suspend fun <T> withAllPermitsHeld(block: () -> T): T = coroutineScope {
            val release = CompletableDeferred<Unit>()
            // limits.max-concurrent-large-sends
            val holders =
                (1..4).map {
                    val started = CompletableDeferred<Unit>()
                    launch {
                        largeSendLimiter.withPermit {
                            started.complete(Unit)
                            release.await()
                        }
                    }
                    started
                }
            holders.awaitAll()
            try {
                block()
            } finally {
                release.complete(Unit)
            }
        }

        beforeEach {
            every { adresseregisteretClient.lookupHerId(any()) } returns null
            every { adresseregisteretClient.lookupHerId(KOMMUNE_HER_ID) } returns kommune
            every { adresseregisteretClient.lookupHerId(FASTLEGE_HER_ID) } returns fastlege
        }

        describe("Codecs") {
            it("gives the kotlinx.serialization JSON codec the same size limit as the default codecs") {
                val decoders =
                    serverCodecConfigurer.readers.filterIsInstance<DecoderHttpMessageReader<*>>().map { it.decoder }
                val defaultLimit = decoders.filterIsInstance<StringDecoder>().first().maxInMemorySize

                val kotlinxDecoders = decoders.filterIsInstance<KotlinSerializationJsonDecoder>()
                kotlinxDecoders.shouldNotBeEmpty()
                kotlinxDecoders.forEach { it.maxInMemorySize shouldBe defaultLimit }
            }
        }

        describe("Security") {
            it("rejects requests without an access token with an ApiError") {
                anonymousClient
                    .get()
                    .uri("/api/lookup/her-id:$FASTLEGE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized
                    .expectHeader()
                    .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo(401)
                    .jsonPath("$.details")
                    .isEqualTo("A valid access token is required")
                    .jsonPath("$.path")
                    .isEqualTo("/api/lookup/her-id:$FASTLEGE_HER_ID")
            }

            it("rejects an invalid access token without the reason from the JWT library") {
                anonymousClient
                    .get()
                    .uri("/api/lookup/her-id:$FASTLEGE_HER_ID")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized
                    .expectHeader()
                    .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo(401)
                    .jsonPath("$.details")
                    .isEqualTo("A valid access token is required")
            }

            describe("scopes") {
                beforeEach {
                    coEvery { mshInternalClient.getMessage(any(), any()) } answers
                        {
                            outgoingMessageMetadata(firstArg(), KOMMUNE_HER_ID, FASTLEGE_HER_ID)
                        }
                    coEvery { mshClient.getStatus(any(), any()) } returns GetStatusResponse(emptyList())
                    coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns emptyList()
                    coEvery { mshClient.markMessageDownloaded(any(), any(), any()) } just Runs
                    coEvery { mshClient.deleteNotifications(any(), any()) } just Runs
                    coEvery { mshClient.getMessage(any(), any()) } answers
                        {
                            incomingMessageMetadata(firstArg(), "DIALOG_HELSEFAGLIG")
                        }
                    coEvery { mshInternalClient.postAppRec(any(), any(), any()) } returns UUID.randomUUID().toString()
                }

                val endpoints: Map<String, (WebTestClient) -> WebTestClient.ResponseSpec> =
                    mapOf(
                        "GET /api/lookup/{identifier}" to
                            { c ->
                                c.get().uri("/api/lookup/her-id:$FASTLEGE_HER_ID").exchange()
                            },
                        "GET /api/messages/out/{messageId}/statuses" to
                            { c ->
                                c.get()
                                    .uri("/api/messages/out/${UUID.randomUUID()}/statuses")
                                    .accept(MediaType.APPLICATION_JSON)
                                    .exchange()
                            },
                        "GET /api/notifications/unread" to
                            { c ->
                                c.get()
                                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID")
                                    .accept(MediaType.APPLICATION_JSON)
                                    .exchange()
                            },
                        "POST /api/notifications/delete" to
                            { c ->
                                c.post()
                                    .uri("/api/notifications/delete")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue("""{"notificationIds":["notification-1"]}""")
                                    .exchange()
                            },
                        "GET /api/messages/in/{messageId}" to
                            { c ->
                                c.get()
                                    .uri("/api/messages/in/${UUID.randomUUID()}?receiverHerId=$KOMMUNE_HER_ID")
                                    .accept(MediaType.APPLICATION_JSON)
                                    .exchange()
                            },
                        // Not a JWE, so an authorized request gets 400
                        "POST /api/messages/in" to
                            { c ->
                                c.post()
                                    .uri("/api/messages/in")
                                    .contentType(APPLICATION_JOSE)
                                    .accept(MediaType.MULTIPART_MIXED)
                                    .bodyValue("not-a-jwe")
                                    .exchange()
                            },
                        "POST /api/messages/out" to
                            { c ->
                                c.post()
                                    .uri("/api/messages/out")
                                    .contentType(MediaType.MULTIPART_FORM_DATA)
                                    .accept(MediaType.TEXT_PLAIN)
                                    .body(
                                        BodyInserters.fromMultipartData(
                                            outgoingMessage(kvitteringSbd(UUID.randomUUID(), UUID.randomUUID()))
                                        )
                                    )
                                    .exchange()
                            },
                        "POST /api/messages/in/{messageId}/downloaded" to
                            { c ->
                                c.post()
                                    .uri(
                                        "/api/messages/in/${UUID.randomUUID()}/downloaded?receiverHerId=$KOMMUNE_HER_ID"
                                    )
                                    .exchange()
                            },
                    )

                fun WebTestClient.ResponseSpec.shouldBeAuthorized() {
                    expectBody().returnResult().status.value() shouldNotBeIn setOf(401, 403)
                }

                fun WebTestClient.ResponseSpec.shouldBeForbidden() {
                    expectStatus()
                        .isForbidden
                        .expectHeader()
                        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"")
                        .expectBody()
                        .jsonPath("$.status")
                        .isEqualTo(403)
                        .jsonPath("$.details")
                        .isEqualTo("Access denied")
                    verify { listOf(mshClient, mshInternalClient) wasNot Called }
                }

                endpoints.forEach { (endpoint, request) ->
                    it("eformidling:dph gives access to $endpoint") {
                        request(client("eformidling:dph")).shouldBeAuthorized()
                    }

                    // The read scope used to give access to the whole API, including sending
                    // messages
                    it("move/dph.read does not give access to $endpoint") {
                        request(client("move/dph.read")).shouldBeForbidden()
                    }

                    it("other scopes do not give access to $endpoint") {
                        request(client("some:other-scope")).shouldBeForbidden()
                    }
                }
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

            // HerIdNotFoundException maps to 404 on the lookup route only; other routes keep 400.
            it("returns 404 when the HER-id is unknown") {
                client().get().uri("/api/lookup/her-id:$UNKNOWN_HER_ID").exchange().expectStatus().isNotFound
            }

            it("returns 404 when Adresseregisteret does not know the HER-id") {
                every { adresseregisteretClient.lookupHerId(UNKNOWN_HER_ID) } throws
                    AddressNotFoundException("Not found")

                client().get().uri("/api/lookup/her-id:$UNKNOWN_HER_ID").exchange().expectStatus().isNotFound
            }

            it("returns the organization itself as parent for an organization") {
                client()
                    .get()
                    .uri("/api/lookup/her-id:$KOMMUNE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.parentHerId")
                    .isEqualTo(KOMMUNE_HER_ID)
                    .jsonPath("$.parentName")
                    .isEqualTo("Helse og omsorg")
                    .jsonPath("$.orgNumber")
                    .isEqualTo(CONSUMER_ORG_NUMBER)
            }

            it("returns 400 for a malformed identifier") {
                client().get().uri("/api/lookup/not-an-identifier").exchange().expectStatus().isBadRequest
            }

            describe("logging") {
                val auditLog = LoggerFactory.getLogger(AUDIT_LOGGER) as Logger
                val applicationLog = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
                val auditEvents = ListAppender<ILoggingEvent>()
                val applicationEvents = ListAppender<ILoggingEvent>()
                val applicationEncoder by lazy {
                    FnrMaskingLogstashEncoder().apply {
                        context = applicationLog.loggerContext
                        start()
                    }
                }

                beforeEach {
                    listOf(auditEvents, applicationEvents).forEach {
                        it.list.clear()
                        it.start()
                    }
                    auditLog.addAppender(auditEvents)
                    applicationLog.addAppender(applicationEvents)
                }

                afterEach {
                    auditLog.detachAppender(auditEvents)
                    applicationLog.detachAppender(applicationEvents)
                }

                /** The audit entry: its message and attributes, the way the audit log writes them. */
                fun auditEntry(): String =
                    auditEvents.list.single().let { "${it.formattedMessage} ${it.argumentArray.joinToString()}" }

                /** The application log, both the messages and the JSON written by the application log's encoder. */
                fun applicationLogLines(): List<String> =
                    applicationEvents.list.flatMap {
                        listOf(it.formattedMessage, applicationEncoder.encode(it).decodeToString())
                    }

                fun lookup(identifier: String) = client().get().uri("/api/lookup/$identifier").exchange()

                // The trace is only there with the OpenTelemetry Java agent, see
                // SpanUrlMaskingTest. Spring Boot
                // applies every NettyServerCustomizer bean to the server.
                it("registers SpanUrlMasking as a customizer of the Netty server") {
                    applicationContext
                        .getBeansOfType(NettyServerCustomizer::class.java)
                        .values
                        .filterIsInstance<SpanUrlMasking>()
                        .shouldNotBeEmpty()
                }

                it("pseudonymizes the fødselsnummer in the audit log") {
                    every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } returns
                        PatientGP(PASIENT_FNR, FASTLEGE_HER_ID)

                    lookup("fastlege-for:$PASIENT_FNR").expectStatus().isOk

                    auditEntry() shouldContain "identifier=fastlege-for:hmac:"
                    auditEntry() shouldNotContain PASIENT_FNR
                }

                it("does not log the fødselsnummer when Fastlegeregisteret fails") {
                    every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } throws
                        FastlegeregisteretApiException(
                            errorCode = "E42",
                            faultMessage = "Ukjent pasient $PASIENT_FNR",
                            message = "Fault for patient $PASIENT_FNR",
                        )

                    lookup("fastlege-for:$PASIENT_FNR").expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)

                    auditEntry() shouldContain "exception=Fault for patient ***********"
                    auditEntry() shouldNotContain PASIENT_FNR
                    applicationLogLines().shouldNotBeEmpty()
                    applicationLogLines().first() shouldBe
                        "Technical error occurred against Fastlegeregisteret for GET ${ApiPaths.LOOKUP}"
                    applicationLogLines().forEach { it shouldNotContain PASIENT_FNR }
                }

                // May be mistyped fødselsnummer, and the parse errors contain them.
                // maskFnr misses them with a hyphen, a space, or a digit too few or too many.
                listOf(
                        "fastlege-for:${PASIENT_FNR.dropLast(1)}0",
                        "her-id:$PASIENT_FNR",
                        "fastlege-for:${PASIENT_FNR.take(6)}-${PASIENT_FNR.drop(6)}",
                        "fastlege-for:${PASIENT_FNR.take(6)} ${PASIENT_FNR.drop(6)}",
                        "fastlege-for:${PASIENT_FNR.dropLast(1)}",
                        "fastlege-for:${PASIENT_FNR}0",
                    )
                    .forEach { identifier ->
                        it("does not log the invalid identifier $identifier") {
                            lookup(identifier).expectStatus().isBadRequest

                            auditEntry() shouldNotContain identifier.substringAfter(':')
                            applicationLogLines().forEach { it shouldNotContain identifier.substringAfter(':') }
                        }
                    }
            }
        }

        describe("POST /api/messages/out") {
            it("sends the dialogmelding with the PDF attachment to MSH") {
                val messageId = UUID.randomUUID()
                val conversationId = UUID.randomUUID()
                val mshMessageId = UUID.randomUUID()
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    mshMessageId.toString()

                sendOutgoing(dialogmeldingSbd(messageId, conversationId), dokumentpakke())
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .isEqualTo(mshMessageId.toString())

                postMessageRequest.captured.contentType shouldBe "application/xml"
                postMessageRequest.captured.contentTransferEncoding shouldBe "base64"
                postMessageRequest.captured.senderHerId shouldBe KOMMUNE_HER_ID
                postMessageRequest.captured.receiverHerIds shouldBe listOf(FASTLEGE_HER_ID)
                postMessageRequest.captured.messageTypeIdentificator shouldBe "DIALOG_HELSEFAGLIG"
                postMessageRequest.captured.applicationName shouldBe "efm-nhn-adapter"
                postMessageRequest.captured.applicationVersion.shouldNotBeBlank()

                val msgHead =
                    BusinessDocumentDeserializer.deserializeMsgHead(
                        String(Base64.getDecoder().decode(postMessageRequest.captured.businessDocument))
                    )
                msgHead.msgInfo.msgId shouldBe messageId.toString()
                msgHead.getConversationRef() shouldBe null

                // The sender is an organization, which is sent as its own parent
                val sender = msgHead.getSender()
                sender.parent.herId shouldBe KOMMUNE_HER_ID
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
                attachment.data.readAllBytes() shouldBe testPdf
            }

            it("sends the dialogmelding to MSH when the sender is a service") {
                every { adresseregisteretClient.lookupHerId(KOMMUNE_HER_ID) } returns kommuneTjeneste
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    UUID.randomUUID().toString()

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
                // MSH is told the HER-id of the service, not of its parent
                postMessageRequest.captured.senderHerId shouldBe KOMMUNE_HER_ID
            }

            it("sends the dialogmelding to MSH when the receiver is a service") {
                every { adresseregisteretClient.lookupHerId(LEGEKONTOR_TJENESTE_HER_ID) } returns legekontorTjeneste
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    UUID.randomUUID().toString()

                sendOutgoing(
                        dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()).apply {
                            receiverHerId = LEGEKONTOR_TJENESTE_HER_ID
                        },
                        dokumentpakke(),
                    )
                    .expectStatus()
                    .isOk

                val msgHead =
                    BusinessDocumentDeserializer.deserializeMsgHead(
                        String(Base64.getDecoder().decode(postMessageRequest.captured.businessDocument))
                    )
                val receiver = msgHead.getReceiver()
                receiver.parent.herId shouldBe LEGEKONTOR_HER_ID
                receiver.child.shouldBeInstanceOf<no.ks.fiks.nhn.msh.OrganizationCommunicationParty>()
                receiver.child.herId shouldBe LEGEKONTOR_TJENESTE_HER_ID
                postMessageRequest.captured.receiverHerIds shouldBe listOf(LEGEKONTOR_TJENESTE_HER_ID)

                // Only "kontakt hos mottaker" is added, since there is no fastlege to refer to
                val roller = msgHead.getDialogmelding().notat.single().rollerRelatertNotat
                roller shouldHaveSize 1
                roller.single().roleToPatient.v shouldBe HelsepersonellsFunksjoner.KONTAKT_HOS_MOTTAKER.verdi
            }

            it("sends the conversation reference to MSH when the dialogmelding is a reply") {
                val conversationId = UUID.randomUUID()
                val parentId = UUID.randomUUID()
                val postMessageRequest = slot<PostMessageRequest>()
                coEvery { mshInternalClient.postMessage(capture(postMessageRequest), consumerTenant()) } returns
                    UUID.randomUUID().toString()

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
                        relatedToMessageId.toString(),
                        capture(postAppRecRequest),
                        consumerTenant(),
                    )
                } returns appRecId.toString()

                sendOutgoing(kvitteringSbd(UUID.randomUUID(), relatedToMessageId))
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .isEqualTo(appRecId.toString())

                postAppRecRequest.captured.appRecStatus shouldBe no.nhn.msh.v3.model.AppRecStatus.OK
                postAppRecRequest.captured.appRecSenderHerId shouldBe KOMMUNE_HER_ID
                postAppRecRequest.captured.applicationName shouldBe "efm-nhn-adapter"
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

            it("returns 400 and sends nothing when the dokumentpakke has too many files") {
                // limits.max-dokumentpakke-files is 100, and the hoveddokument is one of the files
                val vedlegg =
                    (1..100).map {
                        Attachment(
                            "vedlegg-$it.txt",
                            ByteArrayResource(byteArrayOf(1)),
                            MimeType.valueOf("text/plain"),
                        )
                    }

                sendOutgoing(dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()), dokumentpakke(vedlegg = vedlegg))
                    .expectStatus()
                    .isBadRequest
                    .expectBody()
                    .jsonPath("$.details")
                    .isEqualTo("The dokumentpakke has more than 100 files")

                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }

            describe("with a Content-Length below limits.large-send-threshold") {
                // Zeros compress well, so the request is small but unpacks to more
                val largeVedlegg =
                    listOf(
                        Attachment(
                            "vedlegg.txt",
                            ByteArrayResource(ByteArray(DataSize.ofMegabytes(6).toBytes().toInt())),
                            MimeType.valueOf("text/plain"),
                        )
                    )

                it("sends a dokumentpakke that unpacks to more than the threshold") {
                    val mshMessageId = UUID.randomUUID()
                    coEvery { mshInternalClient.postMessage(any(), consumerTenant()) } returns mshMessageId.toString()

                    sendOutgoingWithContentLength(
                            dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()),
                            dokumentpakke(vedlegg = largeVedlegg),
                        )
                        .expectStatus()
                        .isOk
                        .expectBody<String>()
                        .isEqualTo(mshMessageId.toString())
                }

                it("lets a dokumentpakke that unpacks to more than the threshold wait for a permit") {
                    val dokumentpakke = dokumentpakke(vedlegg = largeVedlegg)

                    withAllPermitsHeld {
                        sendOutgoingWithContentLength(
                                dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()),
                                dokumentpakke,
                            )
                            .expectStatus()
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    }

                    coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
                }

                it("never holds back a dokumentpakke that unpacks to less than the threshold") {
                    coEvery { mshInternalClient.postMessage(any(), consumerTenant()) } returns
                        UUID.randomUUID().toString()
                    val dokumentpakke = dokumentpakke()

                    withAllPermitsHeld {
                        sendOutgoingWithContentLength(
                                dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()),
                                dokumentpakke,
                            )
                            .expectStatus()
                            .isOk
                    }
                }
            }

            describe("with a Content-Length above limits.large-send-threshold") {
                it("waits for a permit before the request is read") {
                    // Random bytes do not compress, so the request is above the threshold of 1MB
                    // and
                    // below limits.max-request-size of 2MB
                    val vedlegg =
                        listOf(
                            Attachment(
                                "vedlegg.bin",
                                ByteArrayResource(Random(0).nextBytes(DataSize.ofKilobytes(1500).toBytes().toInt())),
                                MimeType.valueOf("application/octet-stream"),
                            )
                        )
                    val dokumentpakke = dokumentpakke(vedlegg = vedlegg)

                    withAllPermitsHeld {
                        sendOutgoingWithContentLength(
                                dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()),
                                dokumentpakke,
                            )
                            .expectStatus()
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    }

                    coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
                }
            }

            it("returns 408 when the request is not received within limits.max-upload-time") {
                client()
                    .post()
                    .uri("/api/messages/out")
                    .contentType(MediaType.parseMediaType("multipart/form-data;boundary=stalled"))
                    .body(Flux.just("--stalled\r\n".toByteArray()).concatWith(Flux.never()), ByteArray::class.java)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.REQUEST_TIMEOUT)

                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }

            it("returns 413 and sends nothing when a chunked request exceeds limits.max-request-size") {
                // Without a Content-Length, and with each part below the max size
                val maxRequestSize = DataSize.ofMegabytes(2) // limits.max-request-size in the test configuration
                val boundary = "chunked"
                val chunk = DataSize.ofKilobytes(256).toBytes().toInt()
                fun part(name: String, chunks: Int): Flux<ByteArray> =
                    Flux.just(
                            ("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$name\"\r\n" +
                                    "Content-Type: application/octet-stream\r\n\r\n")
                                .toByteArray()
                        )
                        .concatWith(Flux.range(0, chunks).map { ByteArray(chunk) })
                        .concatWith(Flux.just("\r\n".toByteArray()))
                val sent = AtomicLong()
                val body =
                    part(MultipartNames.FORRETNINGSMELDING, 6)
                        .concatWith(part(MultipartNames.DOKUMENTPAKKE, 6))
                        .concatWith(Flux.just("--$boundary--\r\n".toByteArray()))
                        .doOnNext { sent.addAndGet(it.size.toLong()) }

                client()
                    .post()
                    .uri("/api/messages/out")
                    .contentType(MediaType.parseMediaType("multipart/form-data;boundary=$boundary"))
                    .body(body, ByteArray::class.java)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                    .expectBody()
                    .jsonPath("$.details")
                    .isEqualTo("The request exceeds the max size of ${maxRequestSize.toBytes()} bytes")

                // The max size, and the chunk that passed it
                sent.get() shouldBeLessThanOrEqual maxRequestSize.toBytes() + chunk
                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }

            it("deletes the temporary files of a chunked request when it is done") {
                // The files on disk while the message is sent, which shows that they are stored
                // where MultipartTempFileSweeper looks
                var filesWhileSending = emptySet<Path>()
                coEvery { mshInternalClient.postMessage(any(), consumerTenant()) } answers
                    {
                        filesWhileSending = multipartTempFiles()
                        UUID.randomUUID().toString()
                    }
                // Above spring.webflux.multipart.max-in-memory-size in the test configuration, so
                // that
                // the part is stored on disk, and below limits.max-request-size
                val body =
                    MultipartBodyBuilder()
                        .apply {
                            outgoingMessage(dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()), dokumentpakke())
                                .forEach { (name, parts) -> parts.forEach { part(name, it) } }
                            part("padding", ByteArrayResource(ByteArray(DataSize.ofMegabytes(1).toBytes().toInt())))
                                .filename("padding.bin")
                        }
                        .build()
                val filesBefore = multipartTempFiles()

                client()
                    .post()
                    .uri("/api/messages/out")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .exchange()
                    .expectStatus()
                    .isOk

                (filesWhileSending - filesBefore).shouldNotBeEmpty()
                (multipartTempFiles() - filesBefore).shouldBeEmpty()
            }

            it("returns 413 and sends nothing when the dokumentpakke unpacks to more than the max size") {
                // Zeros compress well, so the request is small although the file is too large
                val vedlegg =
                    listOf(
                        Attachment(
                            "vedlegg.txt",
                            ByteArrayResource(ByteArray(DOCUMENTS_MAX_BYTES)),
                            MimeType.valueOf("text/plain"),
                        )
                    )

                sendOutgoing(dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()), dokumentpakke(vedlegg = vedlegg))
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                    .expectBody()
                    .jsonPath("$.details")
                    .isEqualTo("The files in the dokumentpakke exceed the max size of $DOCUMENTS_MAX_BYTES bytes")

                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }

            it("returns 403 and sends nothing when the dialogmelding sender belongs to another organization") {
                val sbd =
                    dialogmeldingSbd(UUID.randomUUID(), UUID.randomUUID()).apply {
                        senderHerId = FASTLEGE_HER_ID
                        receiverHerId = KOMMUNE_HER_ID
                    }

                sendOutgoing(sbd, dokumentpakke()).expectStatus().isForbidden

                // The sender is checked before the receiver is looked up
                verify(exactly = 0) { adresseregisteretClient.lookupHerId(KOMMUNE_HER_ID) }
                coVerify(exactly = 0) { mshInternalClient.postMessage(any(), any()) }
            }

            it("returns 403 and sends nothing when the application receipt sender belongs to another organization") {
                val sbd = kvitteringSbd(UUID.randomUUID(), UUID.randomUUID()).apply { senderHerId = FASTLEGE_HER_ID }

                sendOutgoing(sbd).expectStatus().isForbidden

                coVerify(exactly = 0) { mshInternalClient.postAppRec(any(), any(), any()) }
            }
        }

        describe("GET /api/messages/out/{messageId}/statuses") {
            it("returns the delivery and application receipt status from MSH") {
                val messageId = UUID.randomUUID()
                coEvery { mshInternalClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    outgoingMessageMetadata(messageId, KOMMUNE_HER_ID, FASTLEGE_HER_ID)
                coEvery { mshClient.getStatus(messageId.toString(), consumerTenant()) } returns
                    GetStatusResponse(
                        listOf(
                            StatusInfo(
                                FASTLEGE_HER_ID,
                                true,
                                DeliveryState.ACKNOWLEDGED,
                                AppRecInfo(AppRecStatus.OK, null, null),
                            )
                        )
                    )

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

            it("returns 403 and no status when the sender belongs to another organization") {
                val messageId = UUID.randomUUID()
                coEvery { mshInternalClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    outgoingMessageMetadata(messageId, FASTLEGE_HER_ID, KOMMUNE_HER_ID)

                client()
                    .get()
                    .uri("/api/messages/out/$messageId/statuses")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.getStatus(any(), any()) }
            }

            it("returns the status when MSH leaves out metadata the access check does not need") {
                val messageId = UUID.randomUUID()
                coEvery { mshInternalClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    outgoingMessageMetadata(messageId, KOMMUNE_HER_ID, FASTLEGE_HER_ID)
                        .businessDocumentId(null)
                        .businessDocumentMsgType(null)
                coEvery { mshClient.getStatus(messageId.toString(), consumerTenant()) } returns
                    GetStatusResponse(
                        listOf(
                            StatusInfo(
                                FASTLEGE_HER_ID,
                                true,
                                DeliveryState.ACKNOWLEDGED,
                                AppRecInfo(AppRecStatus.OK, null, null),
                            )
                        )
                    )

                client()
                    .get()
                    .uri("/api/messages/out/$messageId/statuses")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$[0].transportStatus")
                    .isEqualTo("ACKNOWLEDGED")
            }

            it("returns 502 and no status when MSH leaves out the sender") {
                val messageId = UUID.randomUUID()
                coEvery { mshInternalClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    outgoingMessageMetadata(messageId, null, FASTLEGE_HER_ID)

                client()
                    .get()
                    .uri("/api/messages/out/$messageId/statuses")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_GATEWAY)
                    .expectBody()
                    .jsonPath("$.technicalCode")
                    .isEqualTo("E7780")

                coVerify(exactly = 0) { mshClient.getStatus(any(), any()) }
            }

            it("returns 403 without asking MSH when a supplier is delegated through another delegation source") {
                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = "not-trusted")
                    .get()
                    .uri("/api/messages/out/${UUID.randomUUID()}/statuses")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshInternalClient.getMessage(any(), any()) }
                coVerify(exactly = 0) { mshClient.getStatus(any(), any()) }
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

        describe("GET /api/notifications/unread") {
            it("returns every unread notification from MSH, as MSH gives it, and deletes none") {
                val messageId = UUID.randomUUID()
                val newMessage = notification(NotificationType.NEW_MESSAGE, messageId)
                val deliveryState = notification(NotificationType.MESSAGE_DELIVERY_STATE_UPDATED, UUID.randomUUID())
                coEvery { mshClient.getUnreadNotifications(listOf(KOMMUNE_HER_ID), 50, consumerTenant()) } returns
                    listOf(newMessage, deliveryState)

                client()
                    .get()
                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.length()")
                    .isEqualTo(2)
                    .jsonPath("$[0].notificationId")
                    .isEqualTo(newMessage.notificationId)
                    .jsonPath("$[0].relatedMessageId")
                    .isEqualTo(messageId.toString())
                    .jsonPath("$[0].type")
                    .isEqualTo("NEW_MESSAGE")
                    .jsonPath("$[0].notificationReceiverHerId")
                    .isEqualTo(KOMMUNE_HER_ID)
                    .jsonPath("$[0].notificationTriggeredByHerId")
                    .isEqualTo(FASTLEGE_HER_ID)
                    .jsonPath("$[0].createdAt")
                    .isEqualTo("2026-08-21T08:00:00+02:00")
                    .jsonPath("$[1].type")
                    .isEqualTo("MESSAGE_DELIVERY_STATE_UPDATED")

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("gets as many notifications as the client asks for") {
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns emptyList()

                client()
                    .get()
                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID&notificationsToFetch=1000")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .json("[]")

                coVerify { mshClient.getUnreadNotifications(listOf(KOMMUNE_HER_ID), 1000, consumerTenant()) }
            }

            // msh-client throws a NullPointerException when MSH leaves out a field it requires
            it("returns 502 when MSH leaves out a required field") {
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } throws NullPointerException()

                client()
                    .get()
                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_GATEWAY)
                    .expectBody()
                    .jsonPath("$.technicalCode")
                    .isEqualTo("E7780")
            }

            it("returns 400 when the client asks for more notifications than MSH gives") {
                client()
                    .get()
                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID&notificationsToFetch=1001")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshClient.getUnreadNotifications(any(), any(), any()) }
            }

            it("returns 400 when herId is missing") {
                client()
                    .get()
                    .uri("/api/notifications/unread")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }

            // HerIdNotFoundException is only a 404 on the lookup route.
            it("returns 400 when herId is unknown") {
                client()
                    .get()
                    .uri("/api/notifications/unread?herId=$UNKNOWN_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshClient.getUnreadNotifications(any(), any(), any()) }
            }

            it("returns the notifications for a supplier delegated through the trusted delegation source") {
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns emptyList()

                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = trustedDelegationSource())
                    .get()
                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk

                coVerify(exactly = 1) {
                    mshClient.getUnreadNotifications(listOf(KOMMUNE_HER_ID), 50, supplierTenant())
                }
            }

            it("returns 403 without asking MSH when a supplier is delegated through another delegation source") {
                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = "not-trusted")
                    .get()
                    .uri("/api/notifications/unread?herId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.getUnreadNotifications(any(), any(), any()) }
            }

            // AccessDeniedException from SecurityService.assertAccessToHerId maps to 403.
            it("denies access to the notifications of a HER-id belonging to another organization") {
                client()
                    .get()
                    .uri("/api/notifications/unread?herId=$FASTLEGE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.getUnreadNotifications(any(), any(), any()) }
            }
        }

        describe("POST /api/notifications/delete") {
            it("deletes the notifications in MSH") {
                coEvery { mshClient.deleteNotifications(any(), consumerTenant()) } just Runs

                client()
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":["notification-1","notification-2","notification-1"]}""")
                    .exchange()
                    .expectStatus()
                    .isOk

                coVerify(exactly = 1) {
                    mshClient.deleteNotifications(listOf("notification-1", "notification-2"), consumerTenant())
                }
            }

            it("deletes the notifications for a supplier delegated through the trusted delegation source") {
                coEvery { mshClient.deleteNotifications(any(), any()) } just Runs

                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = trustedDelegationSource())
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":["notification-1"]}""")
                    .exchange()
                    .expectStatus()
                    .isOk

                coVerify(exactly = 1) { mshClient.deleteNotifications(listOf("notification-1"), supplierTenant()) }
            }

            it("denies a supplier delegated through another delegation source") {
                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = "not-trusted")
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":["notification-1"]}""")
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("returns 400 when there are no notifications to delete") {
                client()
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":[]}""")
                    .exchange()
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("returns 400 when there are more notifications than MSH deletes at a time") {
                val ids = (0..1000).joinToString(",") { "\"notification-$it\"" }

                client()
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":[$ids]}""")
                    .exchange()
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("returns 413 and reads no further when the body is larger than a list of notification ids can be") {
                client()
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":["${"x".repeat(200 * 1024)}"]}""")
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("returns 400 when a notification id is too long") {
                client()
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"notificationIds":["${"x".repeat(101)}"]}""")
                    .exchange()
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("returns 400 when the input is not valid") {
                client()
                    .post()
                    .uri("/api/notifications/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"ids":"notification-1"}""")
                    .exchange()
                    .expectStatus()
                    .isBadRequest

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }
        }

        describe("GET /api/messages/in/{messageId}") {
            it("returns the metadata of the message for the receiver") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    incomingMessageMetadata(messageId.toString(), "DIALOG_HELSEFAGLIG")

                client()
                    .get()
                    .uri("/api/messages/in/$messageId?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.id")
                    .isEqualTo(messageId.toString())
                    .jsonPath("$.receiverHerId")
                    .isEqualTo(KOMMUNE_HER_ID)
                    .jsonPath("$.senderHerId")
                    .isEqualTo(FASTLEGE_HER_ID)
                    .jsonPath("$.businessDocumentId")
                    .isEqualTo(BUSINESS_DOCUMENT_ID)
                    .jsonPath("$.isAppRec")
                    .isEqualTo(false)
            }

            it("returns the metadata for a supplier delegated through the trusted delegation source") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.getMessage(messageId.toString(), supplierTenant()) } returns
                    incomingMessageMetadata(messageId.toString(), "DIALOG_HELSEFAGLIG")

                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = trustedDelegationSource())
                    .get()
                    .uri("/api/messages/in/$messageId?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.id")
                    .isEqualTo(messageId.toString())
            }

            it("returns 403 without asking MSH when a supplier is delegated through another delegation source") {
                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = "not-trusted")
                    .get()
                    .uri("/api/messages/in/${UUID.randomUUID()}?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.getMessage(any(), any()) }
            }

            it("returns an application receipt as such") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    incomingMessageMetadata(messageId.toString(), "APPREC")

                client()
                    .get()
                    .uri("/api/messages/in/$messageId?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.isAppRec")
                    .isEqualTo(true)
            }

            it("returns 403 when the message is not to the receiver") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    incomingMessageMetadata(messageId.toString(), "DIALOG_HELSEFAGLIG")
                        .copy(receiverHerIds = listOf(LEGEKONTOR_HER_ID))

                client()
                    .get()
                    .uri("/api/messages/in/$messageId?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden
            }

            // msh-client throws a NullPointerException when MSH leaves out a field it requires
            it("returns 502 when MSH leaves out a required field") {
                coEvery { mshClient.getMessage(any(), any()) } throws NullPointerException()

                client()
                    .get()
                    .uri("/api/messages/in/${UUID.randomUUID()}?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_GATEWAY)
                    .expectBody()
                    .jsonPath("$.technicalCode")
                    .isEqualTo("E7780")
            }

            it("returns 502 when MSH does not list the receivers of the message") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.getMessage(messageId.toString(), consumerTenant()) } returns
                    incomingMessageMetadata(messageId.toString(), "DIALOG_HELSEFAGLIG").copy(receiverHerIds = null)

                client()
                    .get()
                    .uri("/api/messages/in/$messageId?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_GATEWAY)
            }

            it("returns 400 when the message id is not a UUID") {
                client()
                    .get()
                    .uri("/api/messages/in/not-a-uuid?receiverHerId=$KOMMUNE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }

            it("denies access to a message for a HER-id belonging to another organization") {
                client()
                    .get()
                    .uri("/api/messages/in/${UUID.randomUUID()}?receiverHerId=$FASTLEGE_HER_ID")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.getMessage(any(), any()) }
            }
        }

        describe("POST /api/messages/in") {
            it("returns 403 without asking MSH when a supplier is delegated through another delegation source") {
                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = "not-trusted")
                    .post()
                    .uri("/api/messages/in")
                    .contentType(APPLICATION_JOSE)
                    .accept(MediaType.MULTIPART_MIXED)
                    .bodyValue(signAndEncrypt("""{"id":"${UUID.randomUUID()}"}"""))
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshInternalClient.getBusinessDocument(any(), any()) }
            }

            // Copy receivers are not supported: the business document names the main receiver
            it("returns 403 when the receiver in the business document belongs to another organization") {
                every { adresseregisteretClient.lookupHerId(LEGEKONTOR_TJENESTE_HER_ID) } returns legekontorTjeneste
                val messageId = UUID.randomUUID()
                val msgHead =
                    BusinessDocumentSerializer.serializeNhnMessage(
                        SendMessageInput(
                            id = messageId,
                            sender = fastlege.toSender(),
                            receiver = legekontorTjeneste.toReceiver(pasient),
                            dialogmelding =
                                DialogmeldingDeserializer.deserializeDialogmelding(String(dialogmeldingXml)),
                            vedlegg = emptyList(),
                            metadataFiler = null,
                            conversationRef = null,
                        )
                    )
                coEvery { mshInternalClient.getBusinessDocument(messageId.toString(), consumerTenant()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument(Base64.getEncoder().encodeToString(msgHead.toByteArray()))

                client()
                    .post()
                    .uri("/api/messages/in")
                    .contentType(APPLICATION_JOSE)
                    .accept(MediaType.MULTIPART_MIXED)
                    .bodyValue(signAndEncrypt("""{"id":"$messageId"}"""))
                    .exchange()
                    .expectStatus()
                    .isForbidden
            }

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
                coEvery { mshInternalClient.getBusinessDocument(messageId.toString(), consumerTenant()) } returns
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
                    parcelService
                        .getAttachments(
                            parcelService.receiveDokumentpakke(parts.getValue(MultipartNames.DOKUMENTPAKKE))
                        )
                        .associateBy { it.filename }
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

            it("returns an application receipt from a party that is not a person") {
                val messageId = UUID.randomUUID()
                val originalMessageId = UUID.randomUUID()
                val appRec =
                    """
                    <AppRec xmlns="http://www.kith.no/xmlstds/apprec/2012-02-15">
                      <MsgType V="APPREC"/>
                      <MIGversion>v1.1 2012-02-15</MIGversion>
                      <GenDate>2026-10-14T12:00:00</GenDate>
                      <Id>$messageId</Id>
                      <Sender>
                        <HCP>
                          <Inst>
                            <Name>Legekontor</Name>
                            <Id>$LEGEKONTOR_HER_ID</Id>
                            <TypeId V="HER"/>
                            <Dept>
                              <Name>Legekontor tjeneste</Name>
                              <Id>$LEGEKONTOR_TJENESTE_HER_ID</Id>
                              <TypeId V="HER"/>
                            </Dept>
                          </Inst>
                        </HCP>
                      </Sender>
                      <Receiver>
                        <HCP>
                          <Inst>
                            <Name>Kommune</Name>
                            <Id>$KOMMUNE_HER_ID</Id>
                            <TypeId V="HER"/>
                          </Inst>
                        </HCP>
                      </Receiver>
                      <Status V="1" DN="OK"/>
                      <OriginalMsgId>
                        <MsgType V="DIALOG_NOTAT"/>
                        <IssueDate>2026-10-14T11:00:00</IssueDate>
                        <Id>$originalMessageId</Id>
                      </OriginalMsgId>
                    </AppRec>
                    """
                        .trimIndent()
                coEvery { mshInternalClient.getBusinessDocument(messageId.toString(), consumerTenant()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument(Base64.getEncoder().encodeToString(appRec.toByteArray()))

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
                        .expectBody<ByteArray>()
                        .returnResult()

                val parts = readMultipart(result.responseHeaders.contentType!!, result.responseBody!!)
                val jws =
                    parcelService.decryptAndVerify(
                        PartUtils.toString(parts.getValue(MultipartNames.FORRETNINGSMELDING))
                    )
                val sbd = jsonMapper.readValue(jws.payload.toString(), StandardBusinessDocument::class.java)
                sbd.type shouldBe BusinessMessageType.DIALOGMELDING_KVITTERING.type
                sbd.senderHerId shouldBe LEGEKONTOR_TJENESTE_HER_ID
                sbd.receiverHerId shouldBe KOMMUNE_HER_ID
                sbd.dialogmeldingKvittering.relatedToMessageId shouldBe originalMessageId.toString()
                sbd.dialogmeldingKvittering.status shouldBe DialogmeldingKvitteringStatus.OK
            }

            fun getIncoming(body: String) =
                client()
                    .post()
                    .uri("/api/messages/in")
                    .contentType(APPLICATION_JOSE)
                    .accept(MediaType.MULTIPART_MIXED)
                    .bodyValue(body)
                    .exchange()

            it("returns 400 when the request is not a JWE") {
                getIncoming("not-a-jwe")
                    .expectStatus()
                    .isBadRequest
                    .expectBody()
                    .jsonPath("$.details")
                    .isEqualTo("JWE decryption failed")
            }

            it("returns 400 when the JWE does not contain a signed JWS") {
                getIncoming(JsonWebEncryption.encrypt("not-a-jws", keystoreHelper.x509Certificate))
                    .expectStatus()
                    .isBadRequest
                    .expectBody()
                    .jsonPath("$.details")
                    .isEqualTo("JWS verification failed")
            }
        }

        describe("POST /api/messages/in/{messageId}/downloaded") {
            it("marks the message as downloaded in MSH and leaves its notification") {
                val messageId = UUID.randomUUID()
                coEvery {
                    mshClient.markMessageDownloaded(messageId.toString(), KOMMUNE_HER_ID, consumerTenant())
                } just Runs

                client()
                    .post()
                    .uri("/api/messages/in/$messageId/downloaded?receiverHerId=$KOMMUNE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .isEqualTo("Message marked as downloaded")

                coVerify(exactly = 1) {
                    mshClient.markMessageDownloaded(messageId.toString(), KOMMUNE_HER_ID, consumerTenant())
                }
                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("returns 502 when MSH fails") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.markMessageDownloaded(messageId.toString(), KOMMUNE_HER_ID, any()) } throws
                    HttpServerException(status = 500, body = "Internal Server Error")

                client()
                    .post()
                    .uri("/api/messages/in/$messageId/downloaded?receiverHerId=$KOMMUNE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_GATEWAY)
            }

            it("marks the message as downloaded for a supplier delegated through the trusted delegation source") {
                val messageId = UUID.randomUUID()
                coEvery { mshClient.markMessageDownloaded(any(), any(), any()) } just Runs

                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = trustedDelegationSource())
                    .post()
                    .uri("/api/messages/in/$messageId/downloaded?receiverHerId=$KOMMUNE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isOk

                coVerify(exactly = 1) {
                    mshClient.markMessageDownloaded(messageId.toString(), KOMMUNE_HER_ID, supplierTenant())
                }
            }

            it("returns 403 without asking MSH when a supplier is delegated through another delegation source") {
                client(supplier = SUPPLIER_ORG_NUMBER, delegationSource = "not-trusted")
                    .post()
                    .uri("/api/messages/in/${UUID.randomUUID()}/downloaded?receiverHerId=$KOMMUNE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.markMessageDownloaded(any(), any(), any()) }
            }

            it("denies marking a message for a HER-id belonging to another organization") {
                client()
                    .post()
                    .uri("/api/messages/in/${UUID.randomUUID()}/downloaded?receiverHerId=$FASTLEGE_HER_ID")
                    .exchange()
                    .expectStatus()
                    .isForbidden

                coVerify(exactly = 0) { mshClient.markMessageDownloaded(any(), any(), any()) }
            }
        }
    })

private const val BUSINESS_DOCUMENT_ID = "9f1c892b-4341-4239-b627-83b7a1eb65ab"

/** MSH metadata for a message from the fastlege to the kommune and the legekontor. */
private fun incomingMessageMetadata(messageId: String, msgType: String) =
    MessageWithMetadata(
        id = messageId,
        contentType = "application/xml",
        receiverHerIds = listOf(KOMMUNE_HER_ID, LEGEKONTOR_HER_ID),
        senderHerId = FASTLEGE_HER_ID,
        businessDocumentId = BUSINESS_DOCUMENT_ID,
        businessDocumentDate = OffsetDateTime.parse("2026-08-21T08:00:00+02:00"),
        businessDocumentMsgType = msgType,
    )

/** An unread MSH notification for [KOMMUNE_HER_ID] about the message [messageId]. */
private fun notification(type: NotificationType, messageId: UUID) =
    UnreadNotification(
        notificationId = UUID.randomUUID().toString(),
        relatedMessageId = messageId.toString(),
        type = type,
        notificationReceiverHerId = KOMMUNE_HER_ID,
        notificationTriggeredByHerId = FASTLEGE_HER_ID,
        description = null,
        createdAt = OffsetDateTime.parse("2026-08-21T08:00:00+02:00"),
    )

/** Matches MSH requests made with a HelseID token for the Maskinporten consumer. */
private fun MockKMatcherScope.consumerTenant() =
    match<RequestParameters> {
        (it.helseId?.tenant as? SingleTenantHelseIdTokenParameters)?.childOrganization == CONSUMER_ORG_NUMBER
    }

/** A HelseID token for the consumer, which a supplier acts on behalf of. */
private fun MockKMatcherScope.supplierTenant() =
    match<RequestParameters> {
        (it.helseId?.tenant as? MultiTenantHelseIdTokenParameters)?.parentOrganization == CONSUMER_ORG_NUMBER
    }

/** MSH metadata for a dialogmelding sent from [senderHerId] to [receiverHerId]. */
private fun outgoingMessageMetadata(messageId: UUID, senderHerId: Int?, receiverHerId: Int) =
    NhnMessage()
        .id(messageId.toString())
        .contentType("application/xml")
        .receiverHerIds(setOf(receiverHerId))
        .senderHerId(senderHerId)
        .businessDocumentId(UUID.randomUUID().toString())
        .businessDocumentGenDate("2026-08-21T08:00:00+02:00")
        .businessDocumentMsgType("DIALOG_HELSEFAGLIG")

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
