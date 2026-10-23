package no.difi.meldingsutveksling.nhn.adapter.integration

import java.time.Duration
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.ks.fiks.helseid.Configuration as HelseIdClientConfiguration
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.helseid.dpop.ProofBuilder
import no.ks.fiks.nhn.ar.rest.AdresseregisteretClient
import no.ks.fiks.nhn.ar.rest.AdresseregisteretService as AdresseregisteretRestService
import no.ks.fiks.nhn.ar.rest.CacheConfig
import no.ks.fiks.nhn.flr.Credentials as FlrCredentials
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretService
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.ClientFactory
import no.ks.fiks.nhn.msh.Configuration as MshConfiguration
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.MshInternalClient

object IntegrationBeans {
    /** Identifies this system towards MSH. */
    private const val SOURCE_SYSTEM = "digdir"

    private const val AR_CACHE_MAX_ENTRIES = 10_000L
    private val AR_CACHE_TTL = Duration.ofMinutes(15)

    fun flrClient(flrConfig: NhnConfig) =
        FastlegeregisteretClient(
            FastlegeregisteretService(flrConfig.url, FlrCredentials(flrConfig.username, flrConfig.password))
        )

    /** Adresseregisteret's REST API is authorized with the same HelseID client as MSH. */
    fun arClient(helseIdConfig: HelseIdConfiguration, arUrl: String): AdresseregisteretClient =
        AdresseregisteretClient(
            AdresseregisteretRestService(arUrl, helseIdConfig.toHelseIdClientConfiguration()),
            CacheConfig(AR_CACHE_MAX_ENTRIES, AR_CACHE_TTL),
        )

    fun mshClient(helseIdConfig: HelseIdConfiguration, mshUrl: String): Client =
        ClientFactory.createClient(MshConfiguration(helseIdConfig, mshUrl, SOURCE_SYSTEM))

    fun mshInternalClient(helseIdConfig: HelseIdConfiguration, mshUrl: String): MshInternalClient =
        MshInternalClient(
            baseUrl = mshUrl,
            sourceSystem = SOURCE_SYSTEM,
            defaultTokenParams = helseIdConfig.tokenParams,
            helseIdClient = HelseIdClient(helseIdConfig.toHelseIdClientConfiguration()),
            proofBuilder = ProofBuilder(helseIdConfig.jwk),
        )

    private fun HelseIdConfiguration.toHelseIdClientConfiguration() =
        HelseIdClientConfiguration(clientId = clientId, jwk = jwk, environment = environment)

    fun mshService(mshClient: Client, internalClient: MshInternalClient) = MshService(mshClient, internalClient)
}
