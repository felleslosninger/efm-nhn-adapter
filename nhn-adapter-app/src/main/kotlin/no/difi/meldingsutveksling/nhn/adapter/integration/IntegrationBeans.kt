package no.difi.meldingsutveksling.nhn.adapter.integration

import java.time.Duration
import no.difi.meldingsutveksling.nhn.adapter.config.MshTimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.config.TimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretRestService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.ks.fiks.helseid.Configuration as HelseIdClientConfiguration
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.helseid.dpop.ProofBuilder
import no.ks.fiks.nhn.ar.rest.AdresseregisteretClient
import no.ks.fiks.nhn.ar.rest.CacheConfig
import no.ks.fiks.nhn.flr.Credentials as FlrCredentials
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretService
import no.ks.fiks.nhn.msh.ApplicationMetadata
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.MshInternalClient

/** Creates the clients for NHN's services, each with the timeouts from its configuration. */
object IntegrationBeans {
    /** Identifies this system towards MSH, together with its version. */
    private const val APPLICATION_NAME = "efm-nhn-adapter"

    /** Used when the build info is missing, as when running from an IDE. */
    private const val UNKNOWN_VERSION = "unknown"

    private const val AR_CACHE_MAX_ENTRIES = 10_000L
    private val AR_CACHE_TTL = Duration.ofMinutes(15)

    /** Shared by MSH and Adresseregisteret, which are authorized with the same HelseID client. */
    fun helseIdClient(helseIdConfig: HelseIdConfiguration, timeout: TimeoutConfig): HelseIdClient =
        HelseIdClient(
            configuration = helseIdConfig.toHelseIdClientConfiguration(),
            httpClient = TimeoutHttpClients.apache(timeout),
            openIdConfiguration = HelseIdOpenIdConfiguration(helseIdConfig.environment.issuer, timeout),
        )

    fun flrClient(flrConfig: NhnConfig) =
        FastlegeregisteretClient(
            TimeoutHttpClients.withCxfTimeouts(flrConfig.timeout) {
                FastlegeregisteretService(flrConfig.url, FlrCredentials(flrConfig.username, flrConfig.password))
            }
        )

    fun arClient(
        helseIdConfig: HelseIdConfiguration,
        helseIdClient: HelseIdClient,
        arUrl: String,
        timeout: TimeoutConfig,
    ): AdresseregisteretClient =
        AdresseregisteretClient(
            AdresseregisteretRestService(arUrl, helseIdConfig.toHelseIdClientConfiguration(), helseIdClient, timeout),
            CacheConfig(AR_CACHE_MAX_ENTRIES, AR_CACHE_TTL),
        )

    /** The high-level client sends its requests through the [MshInternalClient], and so with its timeouts. */
    fun mshClient(internalClient: MshInternalClient, applicationMetadata: ApplicationMetadata): Client =
        Client(internalClient, applicationMetadata)

    fun applicationMetadata(version: String?): ApplicationMetadata =
        ApplicationMetadata(APPLICATION_NAME, version?.takeIf { it.isNotBlank() } ?: UNKNOWN_VERSION)

    fun mshInternalClient(
        helseIdConfig: HelseIdConfiguration,
        helseIdClient: HelseIdClient,
        applicationMetadata: ApplicationMetadata,
        mshUrl: String,
        timeout: MshTimeoutConfig,
    ): MshInternalClient =
        MshInternalClient(
            baseUrl = mshUrl,
            applicationMetadata = applicationMetadata,
            defaultTokenParams = helseIdConfig.tokenParams,
            client = TimeoutHttpClients.msh(timeout),
            helseIdClient = helseIdClient,
            proofBuilder = ProofBuilder(helseIdConfig.jwk),
        )

    private fun HelseIdConfiguration.toHelseIdClientConfiguration() =
        HelseIdClientConfiguration(clientId = clientId, jwk = jwk, environment = environment)

    fun mshService(mshClient: Client, internalClient: MshInternalClient, applicationMetadata: ApplicationMetadata) =
        MshService(mshClient, internalClient, applicationMetadata)
}
