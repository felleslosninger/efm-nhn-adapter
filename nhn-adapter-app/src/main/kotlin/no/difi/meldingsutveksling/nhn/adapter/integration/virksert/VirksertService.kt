package no.difi.meldingsutveksling.nhn.adapter.integration.virksert

import com.github.benmanes.caffeine.cache.Caffeine
import java.security.cert.X509Certificate
import network.oxalis.vefa.peppol.common.model.ParticipantIdentifier
import network.oxalis.vefa.peppol.common.model.ProcessIdentifier
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.config.CacheConfig
import no.difi.virksert.client.BusinessCertificateClient
import no.difi.virksert.client.lang.VirksertClientException

class VirksertService(
    private val virksertClient: BusinessCertificateClient,
    private val process: ProcessIdentifier,
    private val cacheConfig: CacheConfig? = null,
) {
    private val cache =
        cacheConfig?.let { CaffeineCache(config = cacheConfig, loader = ::getCertificateFromVirksert) }
            ?: Cache { getCertificateFromVirksert(it) }

    fun getCertificate(identifier: Iso6523): X509Certificate = cache.get(identifier)

    private fun getCertificateFromVirksert(identifier: Iso6523): X509Certificate {
        try {
            return virksertClient.fetchCertificate(ParticipantIdentifier.of(identifier.toString()), process)
        } catch (_: VirksertClientException) {
            throw VirksertException(String.format("Unable to find %s certificate for: %s", identifier))
        }
    }

    private fun interface Cache {
        fun get(identifier: Iso6523): X509Certificate
    }

    private class CaffeineCache(config: CacheConfig, loader: (Iso6523) -> X509Certificate) : Cache {
        private val cache =
            Caffeine.newBuilder().maximumSize(config.maxSize).expireAfterWrite(config.cacheTtl).build<
                Iso6523,
                X509Certificate,
                > { identifier ->
                loader.invoke(identifier)
            }

        override fun get(identifier: Iso6523): X509Certificate = cache.get(identifier)
    }
}
