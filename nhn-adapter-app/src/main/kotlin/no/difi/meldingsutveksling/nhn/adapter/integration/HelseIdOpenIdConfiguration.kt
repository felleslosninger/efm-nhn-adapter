package no.difi.meldingsutveksling.nhn.adapter.integration

import com.github.benmanes.caffeine.cache.Caffeine
import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import java.net.URI
import java.time.Duration
import no.difi.meldingsutveksling.nhn.adapter.config.TimeoutConfig
import no.ks.fiks.helseid.OpenIdConfiguration

/**
 * HelseID's OpenID configuration, fetched with timeouts. Otherwise as helseid-client's
 * `CachedHttpDiscoveryOpenIdConfiguration`, which waits for HelseID without a time limit.
 */
internal class HelseIdOpenIdConfiguration(private val issuer: String, private val timeout: TimeoutConfig) :
    OpenIdConfiguration {
    private val configuration =
        Caffeine.newBuilder().refreshAfterWrite(REFRESH_INTERVAL).build<String, OIDCProviderMetadata> {
            OIDCProviderMetadata.resolve(
                Issuer(it),
                timeout.connect.toMillis().toInt(),
                timeout.read.toMillis().toInt(),
            )
        }

    override fun getTokenEndpoint(): URI = configuration.get(issuer).tokenEndpointURI

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofHours(6)
    }
}
