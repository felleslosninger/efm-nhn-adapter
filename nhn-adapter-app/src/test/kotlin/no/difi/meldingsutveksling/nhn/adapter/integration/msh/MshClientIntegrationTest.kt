package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.UUID
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.integration.IntegrationBeans
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.helseid.Environment
import no.ks.fiks.nhn.msh.AppRecStatus
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.DeliveryState
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MshException
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.nhn.msh.v2.model.PostAppRecRequest
import no.nhn.msh.v2.model.PostMessageRequest

class MshClientIntegrationTest :
    DescribeSpec({
        val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        val rsaJwk = RSAKeyGenerator(2048).keyID("test-key").generate().toJSONString()

        lateinit var mshClient: Client
        lateinit var mshInternalClient: MshInternalClient
        lateinit var mshService: MshService

        fun setupHelseIdStsStubs() {
            wireMockServer.stubFor(
                get(urlEqualTo("/.well-known/openid-configuration"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "issuer": "${wireMockServer.baseUrl()}",
                                    "token_endpoint": "${wireMockServer.baseUrl()}/connect/token",
                                    "jwks_uri": "${wireMockServer.baseUrl()}/.well-known/openid-configuration/jwks",
                                    "response_types_supported": ["code", "token", "id_token"],
                                    "subject_types_supported": ["public"],
                                    "id_token_signing_alg_values_supported": ["RS256"]
                                }
                                """
                                    .trimIndent()
                            )
                    )
            )

            wireMockServer.stubFor(
                post(urlEqualTo("/connect/token"))
                    .inScenario("DPoP Token")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(
                        aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("DPoP-Nonce", "test-dpop-nonce")
                            .withBody("""{"error": "use_dpop_nonce", "error_description": "Use DPoP nonce"}""")
                    )
                    .willSetStateTo("NONCE_ISSUED")
            )

            wireMockServer.stubFor(
                post(urlEqualTo("/connect/token"))
                    .inScenario("DPoP Token")
                    .whenScenarioStateIs("NONCE_ISSUED")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "access_token": "test-access-token-123",
                                    "expires_in": 3600,
                                    "token_type": "DPoP",
                                    "scope": "nhn:msh"
                                }
                                """
                                    .trimIndent()
                            )
                    )
                    .willSetStateTo(Scenario.STARTED)
            )
        }

        beforeSpec { wireMockServer.start() }

        afterSpec { wireMockServer.stop() }

        beforeEach {
            wireMockServer.resetAll()
            setupHelseIdStsStubs()

            val helseIdConfig =
                HelseIdConfiguration(
                    Environment(wireMockServer.baseUrl(), wireMockServer.baseUrl()),
                    "test-client-id",
                    rsaJwk,
                )

            mshClient = IntegrationBeans.mshClient(helseIdConfig, wireMockServer.baseUrl())
            mshInternalClient = IntegrationBeans.mshInternalClient(helseIdConfig, wireMockServer.baseUrl())
            mshService = IntegrationBeans.mshService(mshClient, mshInternalClient)
        }

        describe("MshClient HTTP requests") {
            it("getMessagesWithMetadata should send GET /Messages with query params and DPoP authorization headers") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages?receiverHerIds=12345&includeMetadata=true"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    [
                                        {
                                            "id": "$messageId",
                                            "contentType": "application/xml",
                                            "receiverHerId": 12345,
                                            "senderHerId": 98765,
                                            "businessDocumentId": "doc-uuid-1",
                                            "businessDocumentGenDate": "2026-08-21T08:00:00+02:00",
                                            "isAppRec": false
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshClient.getMessagesWithMetadata(12345, requestParams)

                result shouldHaveSize 1
                result[0].id shouldBe messageId
                result[0].contentType shouldBe "application/xml"
                result[0].receiverHerId shouldBe 12345
                result[0].senderHerId shouldBe 98765
                result[0].businessDocumentId shouldBe "doc-uuid-1"
                result[0].isAppRec shouldBe false

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages?receiverHerIds=12345&includeMetadata=true"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                        .withHeader("DPoP", matching("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"))
                )
            }

            it("getMessages should send GET /Messages with includeMetadata=false") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages?receiverHerIds=12345&includeMetadata=false"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    [
                                        {
                                            "id": "$messageId",
                                            "receiverHerId": 12345
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshClient.getMessages(12345, requestParams)

                result shouldHaveSize 1
                result[0].id shouldBe messageId
                result[0].receiverHerId shouldBe 12345

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages?receiverHerIds=12345&includeMetadata=false"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("getMessage should send GET /Messages/{id}") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages/$messageId"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    {
                                        "id": "$messageId",
                                        "contentType": "application/xml",
                                        "receiverHerId": 12345,
                                        "senderHerId": 98765,
                                        "businessDocumentId": "doc-uuid-1",
                                        "businessDocumentGenDate": "2026-08-21T08:00:00+02:00",
                                        "isAppRec": false
                                    }
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshClient.getMessage(messageId, requestParams)

                result.id shouldBe messageId
                result.receiverHerId shouldBe 12345
                result.senderHerId shouldBe 98765

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages/$messageId"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("getStatus should send GET /Messages/{id}/status") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages/$messageId/status"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    [
                                        {
                                            "receiverHerId": 12345,
                                            "transportDeliveryState": "Unconfirmed",
                                            "appRecStatus": "Ok"
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshClient.getStatus(messageId, requestParams)

                result shouldHaveSize 1
                result[0].receiverHerId shouldBe 12345
                result[0].deliveryState shouldBe DeliveryState.UNCONFIRMED
                result[0].appRecStatus shouldBe AppRecStatus.OK

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages/$messageId/status"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("markMessageRead should send PUT /Messages/{id}/read/{receiverHerId}") {
                val messageId = UUID.randomUUID()
                val receiverHerId = 12345
                wireMockServer.stubFor(
                    put(urlEqualTo("/Messages/$messageId/read/$receiverHerId")).willReturn(aResponse().withStatus(204))
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                mshClient.markMessageRead(messageId, receiverHerId, requestParams)

                wireMockServer.verify(
                    putRequestedFor(urlEqualTo("/Messages/$messageId/read/$receiverHerId"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("getApplicationReceiptsForMessage should send GET /Messages/{id}/apprec") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages/$messageId/apprec"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    [
                                        {
                                            "receiverHerId": 12345,
                                            "appRecStatus": "Ok",
                                            "appRecErrorList": []
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshClient.getApplicationReceiptsForMessage(messageId, requestParams)

                result shouldHaveSize 1
                result[0].receiverHerId shouldBe 12345
                result[0].status shouldBe no.ks.fiks.hdir.StatusForMottakAvMelding.OK

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages/$messageId/apprec"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("postMessage via MshInternalClient should send POST /Messages with payload and return UUID") {
                val createdId = UUID.randomUUID()
                wireMockServer.stubFor(
                    post(urlEqualTo("/Messages"))
                        .willReturn(
                            aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("\"$createdId\"")
                        )
                )

                val postRequest =
                    PostMessageRequest()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument("dGVzdC1jb250ZW50")

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshInternalClient.postMessage(postRequest, requestParams)

                result shouldBe createdId

                wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/Messages"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Content-Type", containing("application/json"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                        .withRequestBody(matchingJsonPath("$.contentType", equalTo("application/xml")))
                        .withRequestBody(matchingJsonPath("$.contentTransferEncoding", equalTo("base64")))
                        .withRequestBody(matchingJsonPath("$.businessDocument", equalTo("dGVzdC1jb250ZW50")))
                )
            }

            it("postAppRec via MshInternalClient should send POST /Messages/{id}/apprec/{senderHerId}") {
                val messageId = UUID.randomUUID()
                val createdAppRecId = UUID.randomUUID()
                val senderHerId = 54321

                wireMockServer.stubFor(
                    post(urlEqualTo("/Messages/$messageId/apprec/$senderHerId"))
                        .willReturn(
                            aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("\"$createdAppRecId\"")
                        )
                )

                val appRecRequest = PostAppRecRequest().appRecStatus(no.nhn.msh.v2.model.AppRecStatus.OK)

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshInternalClient.postAppRec(messageId, senderHerId, appRecRequest, requestParams)

                result shouldBe createdAppRecId

                wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/Messages/$messageId/apprec/$senderHerId"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Content-Type", containing("application/json"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                        .withRequestBody(matchingJsonPath("$.appRecStatus", equalTo("Ok")))
                )
            }

            it("getBusinessDocument via MshInternalClient should send GET /Messages/{id}/business-document") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages/$messageId/business-document"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    {
                                        "contentType": "application/xml",
                                        "contentTransferEncoding": "base64",
                                        "businessDocument": "PHRlc3Q+ZG9jPC90ZXN0Pg=="
                                    }
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                val result = mshInternalClient.getBusinessDocument(messageId, requestParams)

                result.contentType shouldBe "application/xml"
                result.contentTransferEncoding shouldBe "base64"
                result.businessDocument shouldBe "PHRlc3Q+ZG9jPC90ZXN0Pg=="

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages/$messageId/business-document"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                        .withHeader("Authorization", equalTo("DPoP test-access-token-123"))
                )
            }

            it("should handle error responses from MSH API with MshException") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages/$messageId"))
                        .willReturn(
                            aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""{"error": "Message not found"}""")
                        )
                )

                val requestParams =
                    RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters("123456789")))
                shouldThrow<MshException> { mshClient.getMessage(messageId, requestParams) }
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

            it("getMessagesWithMetadata via MshService should use single-tenant context and retrieve messages") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages?receiverHerIds=12345&includeMetadata=true"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    [
                                        {
                                            "id": "$messageId",
                                            "contentType": "application/xml",
                                            "receiverHerId": 12345,
                                            "senderHerId": 98765,
                                            "businessDocumentId": "doc-uuid-1",
                                            "businessDocumentGenDate": "2026-08-21T08:00:00+02:00",
                                            "isAppRec": false
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val result = mshService.getMessagesWithMetadata(12345, singleTenantContext)
                result shouldHaveSize 1
                result[0].id shouldBe messageId
                result[0].receiverHerId shouldBe 12345

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages?receiverHerIds=12345&includeMetadata=true"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                )
            }

            it("getStatus via MshService should use multi-tenant context and retrieve status") {
                val messageId = UUID.randomUUID()
                wireMockServer.stubFor(
                    get(urlEqualTo("/Messages/$messageId/status"))
                        .willReturn(
                            aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                    """
                                    [
                                        {
                                            "receiverHerId": 12345,
                                            "transportDeliveryState": "Acknowledged",
                                            "appRecStatus": "Ok"
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                )
                        )
                )

                val result = mshService.getStatus(messageId, multiTenantContext)
                result shouldHaveSize 1
                result[0].deliveryState shouldBe DeliveryState.ACKNOWLEDGED
                result[0].appRecStatus shouldBe AppRecStatus.OK

                wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/Messages/$messageId/status"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                )
            }

            it("markMessageRead via MshService should send PUT request with client context") {
                val messageId = UUID.randomUUID()
                val receiverHerId = 12345

                wireMockServer.stubFor(
                    put(urlEqualTo("/Messages/$messageId/read/$receiverHerId")).willReturn(aResponse().withStatus(204))
                )

                mshService.markMessageRead(messageId, receiverHerId, singleTenantContext)

                wireMockServer.verify(
                    putRequestedFor(urlEqualTo("/Messages/$messageId/read/$receiverHerId"))
                        .withHeader("api-version", equalTo("2"))
                        .withHeader("nhn-source-system", equalTo("digdir"))
                )
            }
        }
    })
