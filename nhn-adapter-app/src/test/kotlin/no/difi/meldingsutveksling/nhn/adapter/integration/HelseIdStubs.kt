package no.difi.meldingsutveksling.nhn.adapter.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario

/**
 * Stubs HelseID's OpenID configuration and its token endpoint, which asks for a DPoP nonce before it issues a token.
 * The token is issued after [tokenDelayMillis].
 */
fun WireMockServer.stubHelseIdSts(tokenDelayMillis: Int = 0) {
    stubFor(
        get(urlEqualTo("/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                            "issuer": "${baseUrl()}",
                            "token_endpoint": "${baseUrl()}/connect/token",
                            "jwks_uri": "${baseUrl()}/.well-known/openid-configuration/jwks",
                            "response_types_supported": ["code", "token", "id_token"],
                            "subject_types_supported": ["public"],
                            "id_token_signing_alg_values_supported": ["RS256"]
                        }
                        """
                            .trimIndent()
                    )
            )
    )

    stubFor(
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

    stubFor(
        post(urlEqualTo("/connect/token"))
            .inScenario("DPoP Token")
            .whenScenarioStateIs("NONCE_ISSUED")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(tokenDelayMillis)
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
