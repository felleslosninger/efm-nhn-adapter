package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret

import feign.Request
import feign.RequestInterceptor
import feign.RequestTemplate
import java.util.concurrent.TimeUnit
import no.difi.meldingsutveksling.nhn.adapter.config.TimeoutConfig
import no.ks.fiks.helseid.AccessTokenRequest
import no.ks.fiks.helseid.AccessTokenRequestBuilder
import no.ks.fiks.helseid.Configuration as HelseIdClientConfiguration
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.helseid.TokenType
import no.ks.fiks.helseid.dpop.Endpoint
import no.ks.fiks.helseid.dpop.HttpMethod
import no.ks.fiks.helseid.dpop.ProofBuilder
import no.ks.fiks.helseid.http.DpopHeaderHelper
import no.ks.fiks.nhn.ar.rest.AdresseregisteretService
import no.ks.fiks.nhn.ar.rest.api.CommunicationPartyApi
import no.ks.fiks.nhn.ar.rest.invoker.ApiClient
import no.ks.fiks.nhn.ar.rest.model.CommunicationParty

private const val FOLLOW_REDIRECTS = true

/**
 * Adresseregisteret's REST API, called with timeouts and the adapter's [HelseIdClient].
 *
 * adresseregisteret-rest-client's [AdresseregisteretService] creates its API client and its own HelseID client, and
 * lets neither be configured. This subclass replaces the call to the API, so the parent's clients are never used.
 * Authorized with DPoP tokens, like the parent.
 */
class AdresseregisteretRestService(
    url: String,
    helseIdConfig: HelseIdClientConfiguration,
    helseIdClient: HelseIdClient,
    timeout: TimeoutConfig,
) : AdresseregisteretService(url, helseIdConfig) {
    private val api =
        ApiClient()
            .apply {
                setBasePath(url)
                addAuthorization(
                    "helseid-dpop",
                    DpopAuthInterceptor(url, helseIdClient, ProofBuilder(helseIdConfig.jwk)),
                )
                feignBuilder.options(
                    Request.Options(
                        timeout.connect.toMillis(),
                        TimeUnit.MILLISECONDS,
                        timeout.read.toMillis(),
                        TimeUnit.MILLISECONDS,
                        FOLLOW_REDIRECTS,
                    )
                )
            }
            .buildClient(CommunicationPartyApi::class.java)

    override fun getCommunicationPartyDetails(herId: Int): CommunicationParty? =
        api.apiV1CommunicationpartyHerIdGet(herId)
}

/** As adresseregisteret-rest-client's own interceptor, which is internal to the library. */
private class DpopAuthInterceptor(
    private val baseUrl: String,
    private val helseIdClient: HelseIdClient,
    private val proofBuilder: ProofBuilder,
) : RequestInterceptor {
    private val accessTokenRequest: AccessTokenRequest = AccessTokenRequestBuilder().tokenType(TokenType.DPOP).build()

    override fun apply(template: RequestTemplate) {
        val endpoint = Endpoint(HttpMethod.valueOf(template.method()), requestUrl(template))
        val accessToken = helseIdClient.getAccessToken(accessTokenRequest).accessToken
        val dpopProof = proofBuilder.buildProof(endpoint, accessToken = accessToken)
        DpopHeaderHelper.setHeaders(accessToken, dpopProof) { name, value -> template.header(name, value) }
    }

    private fun requestUrl(template: RequestTemplate): String {
        val path = template.path()
        val separator = if (path.startsWith("/")) "" else "/"
        return baseUrl.trimEnd('/') + separator + path
    }
}
