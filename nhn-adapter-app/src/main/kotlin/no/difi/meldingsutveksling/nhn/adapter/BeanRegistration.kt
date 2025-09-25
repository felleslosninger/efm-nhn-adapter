@file:Suppress("UnusedReceiverParameter")

package no.difi.meldingsutveksling.nhn.adapter

import no.difi.meldingsutveksling.nhn.adapter.beans.IntegrationBeans
import no.difi.meldingsutveksling.nhn.adapter.beans.SecurityBeans
import no.difi.meldingsutveksling.nhn.adapter.config.HelseId
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.ks.fiks.helseid.Configuration
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.get
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException

private fun properties() = BeanRegistrarDsl {
    registerBean<NhnConfig>("ArConfig") {
        Binder.get(env).bind("nhn.services.ar", NhnConfig::class.java).orElseThrow {
            IllegalStateException("ArConfig was not found.")
        }
    }
    registerBean<NhnConfig>("FlrConfig") {
        Binder.get(env).bind("nhn.services.flr", NhnConfig::class.java).orElseThrow {
            IllegalStateException("FlrConfig was not found.")
        }
    }
    registerBean<HelseId> {
        Binder.get(env).bind("oauth2.helse-id", HelseId::class.java).orElseThrow {
            IllegalStateException("HelseId configuration was not found.")
        }
    }
}

private fun security() = BeanRegistrarDsl {
    registerBean<PasswordEncoder> { BCryptPasswordEncoder() }
    registerBean<ReactiveUserDetailsService> { SecurityBeans.userDetailsService(bean()) }

    registerBean { SecurityBeans.userDetailsRepositoryReactiveAuthenticationManager(bean<PasswordEncoder>(), bean()) }

    registerBean { SecurityBeans.securityFilterChain(bean()) }
    registerBean<Configuration> {
        // @TODO it may be time to remove this one. It was used for testy
        SecurityBeans.helseIdConfigurationForTest(bean<HelseId>())
    }
    registerBean { SecurityBeans.helseIdConfiguration(bean<HelseId>()) }

    registerBean {
        // @TODO it may be time to remove this one. It was used for testy
        SecurityBeans.helseIdClient(bean(), bean(), bean())
    }
}

class BeanRegistration :
    BeanRegistrarDsl({
        this.register(properties())
        this.register(security())

        profile("local || dev") { registerBean { IntegrationBeans.flrClient(bean<NhnConfig>("FlrConfig"), this.env) } }

        registerBean { IntegrationBeans.arClient(this.bean<NhnConfig>("ArConfig")) }
        registerBean<HttpClient> { HttpClients.createDefault() }
        registerBean { IntegrationBeans.mshClient(bean(), this.env["nhn.services.msh.url"]!!) }
        registerBean<RouterFunction<*>> {
            coRouter {
                testFlr(bean())
                testAr(bean())
                testDphOut(bean(), bean())
                testRespondApprecFralegekontor(bean())
                arLookup(bean(), bean())
                dphOut(bean(), bean())
                statusCheck(bean())
                incomingReciept(bean())
            }
        }
    })

fun <T> T?.orElseThrowNotFound(message: String): T =
    this ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
