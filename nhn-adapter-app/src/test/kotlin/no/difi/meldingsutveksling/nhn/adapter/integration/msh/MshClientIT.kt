package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.config.MshTimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.config.TimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.integration.IntegrationBeans
import no.difi.meldingsutveksling.nhn.adapter.integration.stubHelseIdSts
import no.difi.meldingsutveksling.nhn.adapter.model.TransportStatus
import no.difi.meldingsutveksling.nhn.adapter.model.toInMessage
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.helseid.Environment
import no.ks.fiks.nhn.msh.AppRecStatus
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.DeliveryState
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MshException
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.NotificationType
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.nhn.msh.v3.model.PostAppRecRequest
import no.nhn.msh.v3.model.PostMessageRequest

private const val SOURCE_SYSTEM = "efm-nhn-adapter 1.2.3"
private const val DPOP_PROOF = "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"

class MshClientIT :
    DescribeSpec({
        val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        val rsaJwk = RSAKeyGenerator(2048).keyID("test-key").generate().toJSONString()
        val requestParams = RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))

        lateinit var mshClient: Client
        lateinit var mshInternalClient: MshInternalClient
        lateinit var mshService: MshService

        beforeSpec { wireMockServer.start() }

        afterSpec { wireMockServer.stop() }

        beforeEach {
            wireMockServer.resetAll()
            wireMockServer.stubHelseIdSts()

            val helseIdConfig =
                HelseIdConfiguration(
                    Environment(wireMockServer.baseUrl(), wireMockServer.baseUrl()),
                    "test-client-id",
                    rsaJwk,
                )

            val timeout = TimeoutConfig(Duration.ofSeconds(5), Duration.ofSeconds(5))
            val helseIdClient = IntegrationBeans.helseIdClient(helseIdConfig, timeout)
            val applicationMetadata = IntegrationBeans.applicationMetadata("1.2.3")
            mshInternalClient =
                IntegrationBeans.mshInternalClient(
                    helseIdConfig,
                    helseIdClient,
                    applicationMetadata,
                    wireMockServer.baseUrl(),
                    MshTimeoutConfig(timeout.connect, timeout.read, Duration.ofSeconds(10)),
                )
            mshClient = IntegrationBeans.mshClient(mshInternalClient, applicationMetadata)
            mshService = IntegrationBeans.mshService(mshClient, mshInternalClient, applicationMetadata)
        }

        fun stubJson(mapping: MappingBuilder, body: String, status: Int = 200) {
            wireMockServer.stubFor(
                mapping.willReturn(
                    aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)
                )
            )
        }

        fun stubUnreadNotifications(vararg notifications: Pair<String, UUID?>) {
            val items =
                notifications.joinToString(",") { (type, messageId) ->
                    """
                    {
                        "notificationId": "${UUID.randomUUID()}",
                        "relatedMessageId": ${messageId?.let { "\"$it\"" }},
                        "type": "$type",
                        "notificationReceiverHerId": 12345,
                        "notificationTriggeredByHerId": 98765,
                        "description": "Ny melding",
                        "createdAt": "2026-08-21T08:00:00+02:00"
                    }
                    """
                }
            stubJson(
                get(urlPathEqualTo("/notifications/unread")).withQueryParam("HerIds", equalTo("12345")),
                """{"unreadNotifications": [$items]}""",
            )
        }

        fun messageJson(messageId: UUID, msgType: String = "DIALOG_HELSEFAGLIG") =
            """
            {
                "id": "$messageId",
                "contentType": "application/xml",
                "receiverHerIds": [12345, 23456],
                "senderHerId": 98765,
                "businessDocumentId": "doc-uuid-1",
                "businessDocumentGenDate": "2026-08-21T08:00:00+02:00",
                "businessDocumentMsgType": "$msgType"
            }
            """
                .trimIndent()

        describe("MshClient HTTP requests") {
            it("getUnreadNotifications should send GET /notifications/unread with API v3 and DPoP headers") {
                val messageId = UUID.randomUUID()
                stubUnreadNotifications("NewMessage" to messageId, "MessageDeliveryStateUpdated" to null)

                val result = mshClient.getUnreadNotifications(listOf(12345), 1000, requestParams)

                result shouldHaveSize 2
                result[0].type shouldBe NotificationType.NEW_MESSAGE
                result[0].relatedMessageId shouldBe messageId.toString()
                result[0].notificationReceiverHerId shouldBe 12345
                result[0].notificationTriggeredByHerId shouldBe 98765
                result[0].description shouldBe "Ny melding"
                result[0].createdAt shouldBe OffsetDateTime.parse("2026-08-21T08:00:00+02:00")
                result[1].type shouldBe NotificationType.MESSAGE_DELIVERY_STATE_UPDATED

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/notifications/unread?HerIds=12345&NotificationsToFetch=1000"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                        .withHeader("DPoP", matching(DPOP_PROOF))
                )
            }

            it("getMessage should send GET /messages/{id} and map the v3 metadata") {
                val messageId = UUID.randomUUID()
                stubJson(get(urlEqualTo("/messages/$messageId")), messageJson(messageId, "APPREC"))

                val result = mshClient.getMessage(messageId.toString(), requestParams)

                result.id shouldBe messageId.toString()
                result.receiverHerIds shouldBe listOf(12345, 23456)
                result.senderHerId shouldBe 98765
                result.businessDocumentMsgType shouldBe "APPREC"
                // The JSON contract towards the clients is unchanged
                with(result.toInMessage(12345)) {
                    receiverHerId shouldBe 12345
                    isAppRec shouldBe true
                }

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/messages/$messageId"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("getStatus should send GET /messages/{id}/status and map an abandoned message") {
                val messageId = UUID.randomUUID()
                stubJson(
                    get(urlEqualTo("/messages/$messageId/status")),
                    """
                    {
                        "statusList": [
                            {
                                "receiverHerId": 12345,
                                "transportDeliveryState": "Abandoned",
                                "sent": true,
                                "apprecInfo": null
                            },
                            {
                                "receiverHerId": 23456,
                                "transportDeliveryState": "Acknowledged",
                                "sent": true,
                                "apprecInfo": {"appRecStatus": "Ok", "appRecId": "${UUID.randomUUID()}"}
                            }
                        ]
                    }
                    """
                        .trimIndent(),
                )

                val result = mshClient.getStatus(messageId.toString(), requestParams).statusList!!

                result shouldHaveSize 2
                result[0].deliveryState shouldBe DeliveryState.ABANDONED
                result[0].toMessageStatus().transportStatus shouldBe TransportStatus.ABANDONED
                result[1].apprecInfo?.appRecStatus shouldBe AppRecStatus.OK

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/messages/$messageId/status"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                )
            }

            it("postMessage via MshInternalClient should send POST /messages with the v3 payload") {
                val createdId = UUID.randomUUID()
                stubJson(post(urlEqualTo("/messages")), """{"id": "$createdId"}""", status = 202)

                val postRequest =
                    PostMessageRequest()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument("dGVzdC1jb250ZW50")
                        .senderHerId(12345)
                        .receiverHerIds(listOf(98765))
                        .messageTypeIdentificator("DIALOG_HELSEFAGLIG")
                        .applicationName("efm-nhn-adapter")
                        .applicationVersion("1.2.3")

                mshInternalClient.postMessage(postRequest, requestParams) shouldBe createdId.toString()

                wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/messages"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                        .withHeader("Content-Type", containing("application/json"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                        .withRequestBody(matchingJsonPath("$.businessDocument", equalTo("dGVzdC1jb250ZW50")))
                        .withRequestBody(matchingJsonPath("$.senderHerId", equalTo("12345")))
                        .withRequestBody(matchingJsonPath("$.receiverHerIds[0]", equalTo("98765")))
                        .withRequestBody(matchingJsonPath("$.messageTypeIdentificator", equalTo("DIALOG_HELSEFAGLIG")))
                )
            }

            it("postAppRec via MshInternalClient should send POST /messages/{id}/apprec with the sender in the body") {
                val messageId = UUID.randomUUID()
                val createdAppRecId = UUID.randomUUID()
                stubJson(post(urlEqualTo("/messages/$messageId/apprec")), """{"id": "$createdAppRecId"}""", 202)

                val appRecRequest =
                    PostAppRecRequest().appRecSenderHerId(54321).appRecStatus(no.nhn.msh.v3.model.AppRecStatus.OK)

                mshInternalClient.postAppRec(messageId.toString(), appRecRequest, requestParams) shouldBe
                    createdAppRecId.toString()

                wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/messages/$messageId/apprec"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                        .withRequestBody(matchingJsonPath("$.appRecSenderHerId", equalTo("54321")))
                        .withRequestBody(matchingJsonPath("$.appRecStatus", equalTo("Ok")))
                )
            }

            it("getBusinessDocument via MshInternalClient should send GET /messages/{id}/business-document") {
                val messageId = UUID.randomUUID()
                stubJson(
                    get(urlEqualTo("/messages/$messageId/business-document")),
                    """
                    {
                        "contentType": "application/xml",
                        "contentTransferEncoding": "base64",
                        "businessDocument": "PHRlc3Q+ZG9jPC90ZXN0Pg=="
                    }
                    """
                        .trimIndent(),
                )

                val result = mshInternalClient.getBusinessDocument(messageId.toString(), requestParams)

                result.contentType shouldBe "application/xml"
                result.contentTransferEncoding shouldBe "base64"
                result.businessDocument shouldBe "PHRlc3Q+ZG9jPC90ZXN0Pg=="

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/messages/$messageId/business-document"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                )
            }

            it("should handle error responses from MSH API with MshException") {
                val messageId = UUID.randomUUID()
                stubJson(get(urlEqualTo("/messages/$messageId")), """{"title": "Message not found"}""", 404)

                shouldThrow<MshException> { mshClient.getMessage(messageId.toString(), requestParams) }
            }
        }

        describe("MshService integration with MshClient") {
            val singleTenantContext =
                object : ClientContext {
                    override val clientId: String = "test-client"
                    override val orgNumber: String = "123456789"
                    override val onBehalfOfOrgNumber: String = "123456789"
                    override val consumer: Iso6523 = Iso6523.parse("0192:123456789")
                    override val supplier: Iso6523? = null
                    override val delegationSource: String? = null
                    override val scopes: Set<String> = setOf("test-scope")
                }

            val multiTenantContext =
                object : ClientContext {
                    override val clientId: String = "test-client"
                    override val orgNumber: String = "987654321"
                    override val onBehalfOfOrgNumber: String = "123456789"
                    override val consumer: Iso6523 = Iso6523.parse("0192:123456789")
                    override val supplier: Iso6523? = Iso6523.parse("0192:987654321")
                    override val delegationSource: String? = "altinn"
                    override val scopes: Set<String> = setOf("test-scope")
                }

            it("getSenderHerId via MshService should read the sender when other metadata is missing") {
                val messageId = UUID.randomUUID()
                stubJson(get(urlEqualTo("/messages/$messageId")), """{"id": "$messageId", "senderHerId": 98765}""")

                mshService.getSenderHerId(messageId, singleTenantContext) shouldBe 98765
            }

            it("getSenderHerId via MshService should report a message without a sender as invalid data") {
                val messageId = UUID.randomUUID()
                stubJson(
                    get(urlEqualTo("/messages/$messageId")),
                    """{"id": "$messageId", "receiverHerIds": [12345]}""",
                )

                shouldThrow<InvalidMshDataException> { mshService.getSenderHerId(messageId, singleTenantContext) }
            }

            it("getUnreadNotifications via MshService should return every notification without deleting any") {
                val messageId = UUID.randomUUID()
                stubUnreadNotifications("NewMessage" to messageId, "MessageSentStateUpdated" to UUID.randomUUID())

                val result = mshService.getUnreadNotifications(12345, 50, singleTenantContext)

                result.map { it.type } shouldBe
                    listOf(NotificationType.NEW_MESSAGE, NotificationType.MESSAGE_SENT_STATE_UPDATED)

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/notifications/unread?HerIds=12345&NotificationsToFetch=50"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                )
                wireMockServer.verify(0, postRequestedFor(urlEqualTo("/notifications/delete")))
            }

            it("getUnreadNotifications via MshService should report a notification without a type as invalid data") {
                stubJson(
                    get(urlPathEqualTo("/notifications/unread")),
                    """
                    {"unreadNotifications": [{"notificationId": "notification-1", "notificationReceiverHerId": 12345}]}
                    """
                        .trimIndent(),
                )

                shouldThrow<InvalidMshDataException> {
                    mshService.getUnreadNotifications(12345, 50, singleTenantContext)
                }
            }

            it("deleteNotifications via MshService should send POST /notifications/delete") {
                wireMockServer.stubFor(
                    post(urlEqualTo("/notifications/delete")).willReturn(aResponse().withStatus(204))
                )

                mshService.deleteNotifications(listOf("notification-1", "notification-2"), singleTenantContext)

                wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/notifications/delete"))
                        .withHeader("api-version", equalTo("3"))
                        .withRequestBody(matchingJsonPath("$.notificationIds[0]", equalTo("notification-1")))
                        .withRequestBody(matchingJsonPath("$.notificationIds[1]", equalTo("notification-2")))
                )
            }

            it("getMessage via MshService should get the metadata of a message") {
                val messageId = UUID.randomUUID()
                stubJson(get(urlEqualTo("/messages/$messageId")), messageJson(messageId))

                val result = mshService.getMessage(messageId, singleTenantContext)

                result.id shouldBe messageId.toString()
                result.toInMessage(12345).isAppRec shouldBe false
            }

            it("getStatus via MshService should use multi-tenant context and retrieve status") {
                val messageId = UUID.randomUUID()
                stubJson(
                    get(urlEqualTo("/messages/$messageId/status")),
                    """
                    {
                        "statusList": [
                            {
                                "receiverHerId": 12345,
                                "transportDeliveryState": "Acknowledged",
                                "sent": true,
                                "apprecInfo": {"appRecStatus": "Ok"}
                            }
                        ]
                    }
                    """
                        .trimIndent(),
                )

                val result = mshService.getStatus(messageId, multiTenantContext)
                result shouldHaveSize 1
                result[0].deliveryState shouldBe DeliveryState.ACKNOWLEDGED
                result[0].apprecInfo?.appRecStatus shouldBe AppRecStatus.OK

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/messages/$messageId/status"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                )
            }

            it("getStatus via MshService should report a status without a delivery state as invalid data") {
                val messageId = UUID.randomUUID()
                stubJson(
                    get(urlEqualTo("/messages/$messageId/status")),
                    """{"statusList": [{"receiverHerId": 12345, "sent": true}]}""",
                )

                shouldThrow<InvalidMshDataException> { mshService.getStatus(messageId, singleTenantContext) }
            }

            it("markMessageDownloaded via MshService should mark the message downloaded and nothing more") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    put(urlEqualTo("/messages/$messageId/downloaded")).willReturn(aResponse().withStatus(204))
                )

                mshService.markMessageDownloaded(messageId, 12345, singleTenantContext)

                wireMockServer.verify(
                    putRequestedFor(urlEqualTo("/messages/$messageId/downloaded"))
                        .withHeader("api-version", equalTo("3"))
                        .withHeader("nhn-source-system", equalTo(SOURCE_SYSTEM))
                        .withRequestBody(matchingJsonPath("$.receiverHerId", equalTo("12345")))
                )
                wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/notifications/unread")))
                wireMockServer.verify(0, postRequestedFor(urlEqualTo("/notifications/delete")))
            }
        }
    })
