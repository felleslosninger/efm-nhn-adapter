package no.difi.meldingsutveksling.nhn.adapter.integration

import java.net.URI
import java.time.Duration
import network.oxalis.vefa.peppol.common.model.ProcessIdentifier
import no.difi.meldingsutveksling.nhn.adapter.DecoratingFlrClient
import no.difi.meldingsutveksling.nhn.adapter.config.CacheConfig
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.config.VirksertConfig
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.integration.virksert.VirksertService
import no.difi.virksert.client.BusinessCertificateClient
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.helseid.dpop.ProofBuilder
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.Credentials
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretService
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.ClientFactory
import no.ks.fiks.nhn.msh.Configuration
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.MshInternalClient
import org.springframework.core.env.Environment

object IntegrationBeans {
    fun flrClient(flrConfig: NhnConfig) =
        FastlegeregisteretClient(
            FastlegeregisteretService(flrConfig.url, Credentials(flrConfig.username, flrConfig.password))
        )

    fun flrClientDecorator(flrClient: FastlegeregisteretClient, env: Environment): DecoratingFlrClient =
        DecoratingFlrClient(flrClient, env.activeProfiles.filter { it in listOf("local", "dev", "test", "prod") })

    fun arClient(arConfig: NhnConfig): AdresseregisteretClient =
        AdresseregisteretClient(
            no.ks.fiks.nhn.ar.AdresseregisteretService(
                arConfig.url,
                no.ks.fiks.nhn.ar.Credentials(arConfig.username, arConfig.password),
            ),
            no.ks.fiks.nhn.ar.CacheConfig(10000, Duration.ofMinutes(15)),
        )

    fun mshClient(helseIdConfig: HelseIdConfiguration, mshUrl: String) =
        ClientFactory.createClient(Configuration(helseIdConfig, mshUrl, "digdir"))

    fun mshInternalClient(helseIdConfig: HelseIdConfiguration, mshUrl: String): MshInternalClient =
        MshInternalClient(
            baseUrl = mshUrl,
            sourceSystem = "digdir",
            defaultTokenParams = helseIdConfig.tokenParams,
            helseIdClient =
                HelseIdClient(
                    no.ks.fiks.helseid.Configuration(
                        clientId = helseIdConfig.clientId,
                        jwk = helseIdConfig.jwk,
                        environment = helseIdConfig.environment,
                    )
                ),
            proofBuilder = ProofBuilder(helseIdConfig.jwk),
        )

    fun mshService(mshClient: Client, internalClient: MshInternalClient) = MshService(mshClient, internalClient)

    fun virksertClient(virksertConfig: VirksertConfig): BusinessCertificateClient =
        BusinessCertificateClient.of(URI.create(virksertConfig.url), virksertConfig.mode)

    fun virksertService(virksertClient: BusinessCertificateClient, virksertConfig: VirksertConfig) =
        VirksertService(
            virksertClient,
            ProcessIdentifier.parse(virksertConfig.process),
            CacheConfig(10000, Duration.ofMinutes(5)),
        )
}
