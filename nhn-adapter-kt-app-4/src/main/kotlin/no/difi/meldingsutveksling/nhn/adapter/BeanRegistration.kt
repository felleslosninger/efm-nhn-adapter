package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.Serializable
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.Credentials
import no.ks.fiks.nhn.flr.Environment
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException


fun  BeanRegistrarDsl.registerProperties()  =
     registerBean<NhnConfig>() {
         Binder.get(env).bind("nhn.services", NhnConfig::class.java).get()
     }



class BeanRegistration : BeanRegistrarDsl ({
    registerProperties()

    registerBean<PasswordEncoder>() {
        BCryptPasswordEncoder()
    }
    registerBean<MapReactiveUserDetailsService>() {
        val passwordEncoder = bean<PasswordEncoder>()
        val user = User.builder().passwordEncoder { passwordEncoder.encode(it) }
            .username("testUser")           
            .password("testPassword")
            .roles()
            .build()
        MapReactiveUserDetailsService(user)
    }
    registerBean<UserDetailsRepositoryReactiveAuthenticationManager>() {
        UserDetailsRepositoryReactiveAuthenticationManager(this.bean<MapReactiveUserDetailsService>()).apply {
            setPasswordEncoder(bean<PasswordEncoder>())
        }
    }
    registerBean<SecurityWebFilterChain>() {
        val serverHttpSecurity = this.bean<ServerHttpSecurity>()
        securityFilterChain(serverHttpSecurity)
    }
    registerBean<FastlegeregisteretClient> {
        val nhnConfig = this.bean<NhnConfig>()
        FastlegeregisteretClient(Environment.TEST,Credentials(nhnConfig.username,nhnConfig.password))
    }
    registerBean<AdresseregisteretClient>{
        val nhnConfig = this.bean<NhnConfig>()
        AdresseregisteretClient(no.ks.fiks.nhn.ar.Environment.TEST, no.ks.fiks.nhn.ar.Credentials(nhnConfig.username,nhnConfig.password))
    }
    registerBean<RouterFunction<*>> {
        val flrClient = this.bean<FastlegeregisteretClient>()
        val arClient = this.bean<AdresseregisteretClient>()
        coRouter {
            testHelloWorld()
            testKotlinX()
            testKotlinxSealedclass()
            testFlr(flrClient)
            testAr(arClient)
            arLookupByFnr(flrClient,arClient)
            arLookupById()
            dphOut()
        }
    }

})

fun <T> T?.orElseThrowNotFound(message: String): T =
    this ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

@Serializable
data class TestKotlinX(val name: String, val value:String)