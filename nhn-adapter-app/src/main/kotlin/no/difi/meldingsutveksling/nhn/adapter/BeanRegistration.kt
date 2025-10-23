@file:Suppress("UnusedReceiverParameter")

package no.difi.meldingsutveksling.nhn.adapter

import java.util.Date
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import no.difi.meldingsutveksling.nhn.adapter.Names.ARCONFIG
import no.difi.meldingsutveksling.nhn.adapter.Names.FLRCONFIG
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.NHN_SERVICE_AR
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.NHN_SERVICE_FLR
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.OAUTH2_HELSE_ID
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.SERVICES_MSH_URL
import no.difi.meldingsutveksling.nhn.adapter.beans.IntegrationBeans
import no.difi.meldingsutveksling.nhn.adapter.beans.SecurityBeans
import no.difi.meldingsutveksling.nhn.adapter.config.HelseId
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.handlers.HerIdNotFound
import no.ks.fiks.helseid.Configuration
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
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
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

private object Names {
    const val ARCONFIG = "ArConfig"
    const val FLRCONFIG = "FlrConfig"
}

private object PropertyNames {
    const val NHN_SERVICE_AR = "nhn.services.ar"
    const val NHN_SERVICE_FLR = "nhn.services.flr"
    const val OAUTH2_HELSE_ID = "oauth2.helse-id"
    const val SERVICES_MSH_URL = "nhn.services.msh.url"
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
}

private fun security() = BeanRegistrarDsl {
    registerBean<PasswordEncoder> { BCryptPasswordEncoder() }
    registerBean<MapReactiveUserDetailsService> {
        SecurityBeans.userDetailsService(bean()) as MapReactiveUserDetailsService
    }
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

private fun integrations() = BeanRegistrarDsl {
    registerBean { IntegrationBeans.arClient(this.bean<NhnConfig>(ARCONFIG)) }
    registerBean<HttpClient> { HttpClients.createDefault() }
    registerBean { IntegrationBeans.mshClient(bean(), this.env[SERVICES_MSH_URL]!!) }
    profile("local || dev || unit-test") {
        registerBean<FastlegeregisteretClient> { IntegrationBeans.flrClient(bean(FLRCONFIG)) }
        registerBean { IntegrationBeans.flrClientDecorator(bean(), this.env) }
    }
}

class BeanRegistration() :
    BeanRegistrarDsl({
        this.register(properties())
        this.register(security())
        this.register(integrations())

        registerBean<RouterFunction<*>> {
            var appPath = this.env["server.app.context-path"] ?: ""
            coRouter {
                    appPath.nest {
                        testFlr(bean())
                        testAr(bean())
                        testDphOut(bean(), bean())
                        testRespondApprecFralegekontor(bean())
                        arLookup(bean(), bean())
                        dphOut(bean(), bean())
                        statusCheck(bean())
                        incomingReciept(bean())
                        POST("/{*path}") {
                            logger.info("I was here")
                            logger.info { "${it.path()} ${it.uri()} ${it.remoteAddress()} ${it.method()}" }

                            ServerResponse.ok().bodyValueAndAwait("Helloooooo")
                        }
                    }
                }
                .filter(nhnErrorFilter())
        }
    })

fun nhnErrorFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> = HandlerFilterFunction { request, next ->
    next.handle(request).onErrorResume {
        val basePath = request.path().substringBeforeLast("/")
        when (it) {
            is IllegalArgumentException ->
                request.toApiError(status = HttpStatus.BAD_REQUEST, it.message ?: "Client error")
            is HerIdNotFound -> {
                when (basePath) {
                    "/arlookup" -> request.toApiError(HttpStatus.NOT_FOUND, "HerId is not found")
                    else -> request.toApiError(HttpStatus.BAD_GATEWAY)
                }
            }
            is AdresseregisteretApiException ->
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: ${it.errorCode}",
                )
            is ResponseStatusException -> throw it
            else -> {
                logger.error("Unexpected error: ${it.message}", it)
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: E7777",
                )
            }
        }.toServerResponse()
    }
}

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
