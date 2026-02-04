@file:Suppress("UnusedReceiverParameter")

package no.difi.meldingsutveksling.nhn.adapter

import java.util.Date
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import no.difi.meldingsutveksling.nhn.adapter.Names.ARCONFIG
import no.difi.meldingsutveksling.nhn.adapter.Names.FLRCONFIG
import no.difi.meldingsutveksling.nhn.adapter.Names.KEYSTORE_CONFIG
import no.difi.meldingsutveksling.nhn.adapter.Names.TRUSTSTORE_CONFIG
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.CRYPTO_KEYSTORE
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.CRYPTO_TRUSTSTORE
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.NHN_SERVICE_AR
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.NHN_SERVICE_FLR
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.OAUTH2_HELSE_ID
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.SERVICES_MSH_URL
import no.difi.meldingsutveksling.nhn.adapter.beans.IntegrationBeans
import no.difi.meldingsutveksling.nhn.adapter.beans.SecurityBeans
import no.difi.meldingsutveksling.nhn.adapter.config.HelseId
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.crypto.CryptoConfig
import no.difi.meldingsutveksling.nhn.adapter.crypto.Dekrypter
import no.difi.meldingsutveksling.nhn.adapter.crypto.Dekryptering
import no.difi.meldingsutveksling.nhn.adapter.crypto.Kryptering
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnKeystore
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnTrustStore
import no.difi.meldingsutveksling.nhn.adapter.crypto.SignatureValidator
import no.ks.fiks.helseid.Configuration
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.get
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

private object Names {
    const val ARCONFIG = "ArConfig"
    const val FLRCONFIG = "FlrConfig"
    const val KEYSTORE_CONFIG = "KeystoreConfig"
    const val TRUSTSTORE_CONFIG = "TrustStoreConfig"
}

private object PropertyNames {
    const val NHN_SERVICE_AR = "nhn.services.ar"
    const val NHN_SERVICE_FLR = "nhn.services.flr"
    const val OAUTH2_HELSE_ID = "oauth2.helse-id"
    const val SERVICES_MSH_URL = "nhn.services.msh.url"
    const val CRYPTO_KEYSTORE = "crypto.keystore"
    const val CRYPTO_TRUSTSTORE = "crypto.truststore"
}

private fun properties() = BeanRegistrarDsl {
    registerBean<NhnConfig>(ARCONFIG) {
        Binder.get(env).bind(NHN_SERVICE_AR, NhnConfig::class.java).orElseThrow {
            IllegalStateException("ArConfig was not found.")
        }
    }
    registerBean<NhnConfig>(FLRCONFIG) {
        Binder.get(env).bind(NHN_SERVICE_FLR, NhnConfig::class.java).orElseThrow {
            IllegalStateException("FlrConfig was not found.")
        }
    }
    registerBean<HelseId> {
        Binder.get(env).bind(OAUTH2_HELSE_ID, HelseId::class.java).orElseThrow {
            IllegalStateException("HelseId configuration was not found.")
        }
    }
    registerBean<CryptoConfig>(KEYSTORE_CONFIG) {
        Binder.get(env).bind(CRYPTO_KEYSTORE, CryptoConfig::class.java).orElseThrow {
            IllegalStateException("Cryptography configuration was not found.")
        }
    }
    registerBean<CryptoConfig>(TRUSTSTORE_CONFIG) {
        Binder.get(env).bind(CRYPTO_TRUSTSTORE, CryptoConfig::class.java).orElseThrow {
            IllegalStateException("Cryptography configuration was not found.")
        }
    }
}

private fun security() = BeanRegistrarDsl {
    registerBean<PasswordEncoder> { BCryptPasswordEncoder() }
    registerBean<MapReactiveUserDetailsService> {
        SecurityBeans.userDetailsService(bean()) as MapReactiveUserDetailsService
    }
    registerBean { SecurityBeans.userDetailsRepositoryReactiveAuthenticationManager(bean<PasswordEncoder>(), bean()) }
    profile(expression = "local || dev || unit-test") {
        registerBean { SecurityBeans.securityFilterChain(bean(), includeBasicSecurity = true) }
    }
    profile(expression = "prod || test") { registerBean { SecurityBeans.securityFilterChain(bean()) } }

    registerBean<Configuration> {
        // @TODO it may be time to remove this one. It was used for test
        SecurityBeans.helseIdConfigurationForTest(bean<HelseId>())
    }
    registerBean { SecurityBeans.helseIdConfiguration(bean<HelseId>()) }
    registerBean {
        // @TODO it may be time to remove this one. It was used for test
        SecurityBeans.helseIdClient(bean(), bean(), bean())
    }
}

private fun crypto() = BeanRegistrarDsl {
    registerBean<NhnKeystore> { NhnKeystore(bean(KEYSTORE_CONFIG)) }
    registerBean<NhnTrustStore> { NhnTrustStore(bean(TRUSTSTORE_CONFIG)) }
    registerBean<SignatureValidator> { SignatureValidator(bean()) }
    registerBean { Kryptering() }

    profile(expression = "unit-test") {
        registerBean<Dekrypter> {
            object : Dekrypter {
                override fun dekrypter(byteArray: ByteArray): ByteArray = byteArray
            }
        }
    }

    profile(expression = "!unit-test") { registerBean<Dekrypter> { Dekryptering(bean()) } }
}

private fun integrations() = BeanRegistrarDsl {
    registerBean { IntegrationBeans.arClient(this.bean<NhnConfig>(ARCONFIG)) }
    registerBean<HttpClient> { HttpClients.createDefault() }
    registerBean { IntegrationBeans.mshClient(bean(), this.env[SERVICES_MSH_URL]!!) }
    profile(expression = "local || dev || unit-test || test ") {
        registerBean<FastlegeregisteretClient> { IntegrationBeans.flrClient(bean(FLRCONFIG)) }
        registerBean { IntegrationBeans.flrClientDecorator(bean(), this.env) }
    }
}

class BeanRegistration() :
    BeanRegistrarDsl({
        this.register(properties())
        this.register(crypto())
        this.register(security())
        this.register(integrations())

        profile(expression = "local || dev || unit-test || test") {
            registerBean<RouterFunction<*>> {
                coRouter {
                    testFlr(bean())
                    testAr(bean())
                    testDphOut(bean(), bean())
                    testRespondApprecFralegekontor(bean())
                    testReadMessageFromFastlegekontoret(bean())
                }
            }
        }

        registerBean<RouterFunction<*>> {
            coRouter {
                    arLookup(bean(), bean(), bean())
                    dphOut(bean(), bean(), bean(), bean())
                    statusCheck(bean())
                    incomingReciept(bean(), bean(), bean())
                }
                .filter(nhnErrorFilter())
        }
    })

fun <T> T?.orElseThrowNotFound(message: String): T =
    this ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

@Serializable
data class ApiError(
    val status: Int,
    val message: String?,
    val error: String?,
    val path: String?,
    val requestId: String?,
    val timestamp: String,
)

fun ApiError.toServerResponse(): Mono<ServerResponse> = ServerResponse.status(this.status).bodyValue(this)

@OptIn(ExperimentalTime::class)
fun ServerRequest.toApiError(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, message: String = ""): ApiError =
    ApiError(
        timestamp = Date().toString(),
        status = status.value(),
        error = status.reasonPhrase,
        message = message,
        path = this.path(),
        requestId = this.exchange().request.id,
    )
