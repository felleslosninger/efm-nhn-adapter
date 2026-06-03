@file:Suppress("UnusedReceiverParameter")

package no.difi.meldingsutveksling.nhn.adapter

import java.util.Date
import kotlin.time.ExperimentalTime
import no.difi.certvalidator.BusinessCertificateValidator
import no.difi.certvalidator.BusinessCertificateValidatorFactory
import no.difi.meldingsutveksling.nhn.adapter.Names.ARCONFIG
import no.difi.meldingsutveksling.nhn.adapter.Names.FLRCONFIG
import no.difi.meldingsutveksling.nhn.adapter.Names.SECURITY_CONFIG
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.NHN_SERVICE_AR
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.NHN_SERVICE_FLR
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.OAUTH2_HELSE_ID
import no.difi.meldingsutveksling.nhn.adapter.PropertyNames.SERVICES_MSH_URL
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.config.CertificateConfig
import no.difi.meldingsutveksling.nhn.adapter.config.HelseId
import no.difi.meldingsutveksling.nhn.adapter.config.KeystoreConfig
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.config.SecurityConfig
import no.difi.meldingsutveksling.nhn.adapter.config.TempFileConfig
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.ParcelService
import no.difi.meldingsutveksling.nhn.adapter.integration.IntegrationBeans
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.model.ApiError
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityBeans
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.config.KeystoreProperties
import no.difi.move.common.io.InMemoryWithTempFileFallbackResourceFactory
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.io.ApplicationResourceLoader
import org.springframework.core.env.get
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter
import reactor.core.publisher.Mono

private object Names {
    const val ARCONFIG = "ArConfig"
    const val FLRCONFIG = "FlrConfig"
    const val SECURITY_CONFIG = "SecurityConfig"
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
    registerBean<SecurityConfig>(SECURITY_CONFIG) {
        Binder.get(env).bind("security", SecurityConfig::class.java).orElseThrow {
            IllegalStateException("SecurityConfig configuration was not found.")
        }
    }
    registerBean<TempFileConfig>("TempFileConfig") {
        Binder.get(env).bind("temp", TempFileConfig::class.java).orElseThrow {
            IllegalStateException("TempFileConfig configuration was not found.")
        }
    }

    registerBean<KeystoreConfig>("KeystoreConfig") {
        Binder.get(env).bind("keystore", KeystoreConfig::class.java).orElseThrow {
            IllegalStateException("KeystoreConfig configuration was not found.")
        }
    }

    registerBean<CertificateConfig>("CertificateValidationConfig") {
        Binder.get(env).bind("certificate", CertificateConfig::class.java).orElseThrow {
            IllegalStateException("certificate configuration was not found.")
        }
    }

    registerBean { ApplicationResourceLoader.get() }

    registerBean<KeystoreProperties> {
        val resourceLoader: ResourceLoader = bean()
        val config: KeystoreConfig = bean()

        KeystoreProperties()
            .setAlias(config.alias)
            .setPassword(config.password)
            .setType(config.type)
            .setPath(resourceLoader.getResource(config.path))
            .setLockProvider(config.lockProvider)
    }
}

private fun security() = BeanRegistrarDsl {
    registerBean { SecurityBeans.securityFilterChain(bean(), bean()) }
    registerBean { SecurityBeans.helseIdConfiguration(bean<HelseId>()) }
}

private fun integrations() = BeanRegistrarDsl {
    registerBean { IntegrationBeans.arClient(this.bean<NhnConfig>(ARCONFIG)) }
    registerBean { AdresseregisteretService(bean(), bean()) }
    registerBean<HttpClient> { HttpClients.createDefault() }
    registerBean { IntegrationBeans.mshClient(bean(), this.env[SERVICES_MSH_URL]!!) }
    registerBean { IntegrationBeans.mshInternalClient(bean(), this.env[SERVICES_MSH_URL]!!) }
    registerBean { IntegrationBeans.mshService(bean(), bean()) }
    registerBean<FastlegeregisteretClient> { IntegrationBeans.flrClient(bean(FLRCONFIG)) }
}

class BeanRegistration :
    BeanRegistrarDsl({
        this.register(properties())
        this.register(security())
        this.register(integrations())

        registerBean { businessCertificateValidator(bean()) }
        registerBean { AuditLogService(bean()) }
        registerBean { inMemoryWithTempFileFallbackResourceFactory(bean()) }
        registerBean { SecurityService(bean()) }
        registerBean { KeystoreHelper(bean()) }
        registerBean { ParcelService(bean(), bean(), bean(), bean(), bean(), bean()) }
        registerBean { InHandler(bean(), bean(), bean(), bean(), bean()) }
        registerBean { OutHandler(bean(), bean(), bean(), bean(), bean()) }
        registerBean { LookupHandler(bean(), bean(), bean()) }
        registerBean<RouterFunction<*>> {
            coRouter {
                    inHandler(bean())
                    outHandler(bean())
                    lookupHandler(bean())
                }
                .filter(nhnErrorFilter())
        }
    })

fun inMemoryWithTempFileFallbackResourceFactory(config: TempFileConfig): InMemoryWithTempFileFallbackResourceFactory =
    InMemoryWithTempFileFallbackResourceFactory(config.threshold, config.initialBufferSize, config.directory)

fun businessCertificateValidator(config: CertificateConfig): BusinessCertificateValidator =
    BusinessCertificateValidatorFactory().createValidator(config.mode)

fun ApiError.toServerResponse(): Mono<ServerResponse> = ServerResponse.status(this.status).bodyValue(this)

@OptIn(ExperimentalTime::class)
fun ServerRequest.toApiError(
    status: HttpStatus,
    error: FeilmeldingForApplikasjonskvittering,
    details: String? = null,
): ApiError =
    ApiError(
        timestamp = Date().toString(),
        status = status.value(),
        reason = status.reasonPhrase,
        errorCode = error.verdi,
        message = error.navn,
        details = details,
        path = this.path(),
        requestId = this.exchange().request.id,
    )
