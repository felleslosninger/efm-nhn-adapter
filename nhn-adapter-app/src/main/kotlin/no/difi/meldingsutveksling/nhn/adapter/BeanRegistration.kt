package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.Serializable
import no.ks.fiks.helseid.CachedHttpDiscoveryOpenIdConfiguration
import no.ks.fiks.helseid.Configuration
import no.ks.fiks.helseid.Environment
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretService
import no.ks.fiks.nhn.flr.Credentials
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretService
import no.ks.fiks.nhn.msh.ClientFactory
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.get
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException

private fun properties() = BeanRegistrarDsl {
    registerBean<NhnConfig>("ArConfig") { Binder.get(env).bind("nhn.services.ar", NhnConfig::class.java).get() }
    registerBean<NhnConfig>("FlrConfig") { Binder.get(env).bind("nhn.services.flr", NhnConfig::class.java).get() }

    registerBean<HelseId> { Binder.get(env).bind("oauth2.helse-id", HelseId::class.java).get() }
}

private fun security() = BeanRegistrarDsl {
    registerBean<PasswordEncoder> { BCryptPasswordEncoder() }
    registerBean {
        val passwordEncoder = bean<PasswordEncoder>()
        val user =
            User.builder()
                .passwordEncoder { passwordEncoder.encode(it) }
                .username("testUser")
                .password("testPassword")
                .roles()
                .build()
        MapReactiveUserDetailsService(user)
    }
    registerBean {
        UserDetailsRepositoryReactiveAuthenticationManager(this.bean<MapReactiveUserDetailsService>()).apply {
            setPasswordEncoder(bean<PasswordEncoder>())
        }
    }
    registerBean { securityFilterChain(bean()) }
    registerBean {
        val helseId = bean<HelseId>()
        Configuration(
            helseId.clientId,
            helseId.privateKey,
            no.ks.fiks.helseid.Environment(helseId.issuer, helseId.audience),
        )
    }
    registerBean {
        val helseId = bean<HelseId>()
        HelseIdConfiguration(Environment(helseId.issuer, helseId.audience), helseId.clientId, helseId.privateKey)
    }
    registerBean {
        val helseId = bean<HelseId>()
        HelseIdClient(bean(), bean(), CachedHttpDiscoveryOpenIdConfiguration(helseId.issuer))
    }
}

class BeanRegistration :
    BeanRegistrarDsl({
        this.register(properties())
        this.register(security())

        profile("local || dev") {
            registerBean {
                val flrConfig = this.bean<NhnConfig>("FlrConfig")
                val flrClient =
                    FastlegeregisteretClient(
                        FastlegeregisteretService(flrConfig.url, Credentials(flrConfig.username, flrConfig.password))
                    )
                DecoratingFlrClient(
                    flrClient,
                    this.env.activeProfiles.filter { it in listOf("local", "dev", "test", "prod") },
                )
            }
        }
        registerBean {
            val arConfig = this.bean<NhnConfig>("ArConfig")
            AdresseregisteretClient(
                AdresseregisteretService(
                    arConfig.url,
                    no.ks.fiks.nhn.ar.Credentials(arConfig.username, arConfig.password),
                )
            )
        }
        registerBean<HttpClient>(HttpClients::createDefault)

        registerBean {
            ClientFactory.createClient(
                no.ks.fiks.nhn.msh.Configuration(bean(), this.env["nhn.services.msh.url"]!!, "digdir")
            )
        }
        registerBean<RouterFunction<*>> {
            coRouter {
                testKotlinX()
                testKotlinxSealedclass()
                testFlr(bean())
                testAr(bean())
                testDphOut(bean(), bean())
                arLookupByFnr(bean(), bean())
                arLookupById()
                dphOut(bean(), bean())
            }
        }
    })

fun <T> T?.orElseThrowNotFound(message: String): T =
    this ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

@Serializable data class TestKotlinX(val name: String, val value: String)
