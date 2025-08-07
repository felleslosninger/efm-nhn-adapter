package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.Serializable
import no.ks.fiks.helseid.CachedHttpDiscoveryOpenIdConfiguration
import no.ks.fiks.helseid.Configuration
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.Credentials
import no.ks.fiks.nhn.flr.Environment
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException



private fun properties() =
    BeanRegistrarDsl {
        registerBean<NhnConfig> { Binder.get(env).bind("nhn.services", NhnConfig::class.java).get() }
        registerBean<HelseId> { Binder.get(env).bind("oauth2.helse-id", HelseId::class.java).get() }
    }


private  fun security() =
    BeanRegistrarDsl {
        registerBean<PasswordEncoder>() {
            BCryptPasswordEncoder()
        }
        registerBean() {
            val passwordEncoder = bean<PasswordEncoder>()
            val user = User.builder().passwordEncoder { passwordEncoder.encode(it) }
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
        registerBean {
            securityFilterChain(bean())
        }
        registerBean {
            val helseId = bean<HelseId>()
            Configuration(helseId.clientId,helseId.privateKey, no.ks.fiks.helseid.Environment(helseId.issuer,helseId.audience))
        }

    }



class BeanRegistration : BeanRegistrarDsl ({
    this.register(properties())
    this.register(security())

    registerBean {
        val nhnConfig = this.bean<NhnConfig>()
        FastlegeregisteretClient(Environment.TEST,Credentials(nhnConfig.username,nhnConfig.password))
    }
    registerBean {
        val nhnConfig = this.bean<NhnConfig>()
        AdresseregisteretClient(no.ks.fiks.nhn.ar.Environment.TEST, no.ks.fiks.nhn.ar.Credentials(nhnConfig.username,nhnConfig.password))
    }
    registerBean<HttpClient>(HttpClients::createDefault)
    registerBean {
        val helseId = bean<HelseId>()
        HelseIdClient(bean(),
            bean(),
            CachedHttpDiscoveryOpenIdConfiguration(helseId.issuer)
        );
    }
    registerBean<RouterFunction<*>> {
        coRouter {
            testHelloWorld()
            testKotlinX()
            testKotlinxSealedclass()
            testFlr(bean())
            testAr(bean())
            testDphOut(bean(),bean())
            arLookupByFnr(bean(),bean())
            arLookupById()
            dphOut()
        }
    }

})

fun <T> T?.orElseThrowNotFound(message: String): T =
    this ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

@Serializable
data class TestKotlinX(val name: String, val value:String)