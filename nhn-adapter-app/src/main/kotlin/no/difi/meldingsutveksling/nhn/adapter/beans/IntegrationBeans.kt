package no.difi.meldingsutveksling.nhn.adapter.beans

import no.difi.meldingsutveksling.nhn.adapter.DecoratingFlrClient
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretService
import no.ks.fiks.nhn.flr.Credentials
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretService
import no.ks.fiks.nhn.msh.ClientFactory
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import org.springframework.core.env.Environment

object IntegrationBeans {
    fun flrClient(flrConfig: NhnConfig, env: Environment): DecoratingFlrClient {
        val flrClient =
            FastlegeregisteretClient(
                FastlegeregisteretService(flrConfig.url, Credentials(flrConfig.username, flrConfig.password))
            )
        return DecoratingFlrClient(
            flrClient,
            env.activeProfiles.filter { it in listOf("local", "dev", "test", "prod") },
        )
    }

    fun arClient(arConfig: NhnConfig): AdresseregisteretClient =
        AdresseregisteretClient(
            AdresseregisteretService(arConfig.url, no.ks.fiks.nhn.ar.Credentials(arConfig.username, arConfig.password))
        )

    fun mshClient(helseIdConfig: HelseIdConfiguration, mshUrl: String) =
        ClientFactory.createClient(no.ks.fiks.nhn.msh.Configuration(helseIdConfig, mshUrl, "digdir"))
}
