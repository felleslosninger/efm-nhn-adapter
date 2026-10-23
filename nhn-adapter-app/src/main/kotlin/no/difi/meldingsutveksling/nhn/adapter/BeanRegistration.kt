package no.difi.meldingsutveksling.nhn.adapter

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import no.difi.certvalidator.BusinessCertificateValidatorFactory
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
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityBeans
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.cert.KeystoreHelper
import no.difi.move.common.config.KeystoreProperties
import no.difi.move.common.io.InMemoryWithTempFileFallbackResourceFactory
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.io.ApplicationResourceLoader
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpHeaders

private object BeanNames {
    const val FLR_CONFIG = "FlrConfig"
}

private object PropertyPrefixes {
    const val NHN_SERVICE_FLR = "nhn.services.flr"
    const val OAUTH2_HELSE_ID = "oauth2.helse-id"
    const val SECURITY = "security"
    const val TEMP_FILE = "temp"
    const val KEYSTORE = "keystore"
    const val CERTIFICATE = "certificate"
}

private const val SERVICES_AR_URL = "nhn.services.ar.url"
private const val SERVICES_MSH_URL = "nhn.services.msh.url"

private const val OPENAPI_SECURITY_SCHEME = "maskinporten"

private fun properties() = BeanRegistrarDsl {
    registerBean<NhnConfig>(BeanNames.FLR_CONFIG) { env.bindRequired(PropertyPrefixes.NHN_SERVICE_FLR) }
    registerBean<HelseId> { env.bindRequired(PropertyPrefixes.OAUTH2_HELSE_ID) }
    registerBean<SecurityConfig> { env.bindRequired(PropertyPrefixes.SECURITY) }
    registerBean<TempFileConfig> { env.bindRequired(PropertyPrefixes.TEMP_FILE) }
    registerBean<KeystoreConfig> { env.bindRequired(PropertyPrefixes.KEYSTORE) }
    registerBean<CertificateConfig> { env.bindRequired(PropertyPrefixes.CERTIFICATE) }

    registerBean { ApplicationResourceLoader.get() }
    registerBean { keystoreProperties(bean(), bean()) }
}

private fun security() = BeanRegistrarDsl {
    registerBean { SecurityBeans.securityFilterChain(bean(), bean()) }
    registerBean { SecurityBeans.helseIdConfiguration(bean()) }
}

private fun integrations() = BeanRegistrarDsl {
    registerBean { IntegrationBeans.arClient(bean(), env.getRequiredProperty(SERVICES_AR_URL)) }
    registerBean<FastlegeregisteretClient> { IntegrationBeans.flrClient(bean(BeanNames.FLR_CONFIG)) }
    registerBean { AdresseregisteretService(bean(), bean()) }
    registerBean { IntegrationBeans.mshClient(bean(), env.getRequiredProperty(SERVICES_MSH_URL)) }
    registerBean { IntegrationBeans.mshInternalClient(bean(), env.getRequiredProperty(SERVICES_MSH_URL)) }
    registerBean { IntegrationBeans.mshService(bean(), bean()) }
}

private fun services() = BeanRegistrarDsl {
    registerBean { BusinessCertificateValidatorFactory().createValidator(bean<CertificateConfig>().mode) }
    registerBean { AuditLogService(bean()) }
    registerBean { tempFileResourceFactory(bean()) }
    registerBean { SecurityService(bean(), bean()) }
    registerBean { KeystoreHelper(bean()) }
    registerBean { ParcelService(bean(), bean(), bean(), bean(), bean(), bean()) }
}

private fun handlers() = BeanRegistrarDsl {
    registerBean { InHandler(bean(), bean(), bean(), bean(), bean()) }
    registerBean { OutHandler(bean(), bean(), bean(), bean(), bean(), bean()) }
    registerBean { LookupHandler(bean(), bean(), bean()) }
}

private fun openApi() = BeanRegistrarDsl {
    registerBean { openApiDefinition() }
    registerBean { GroupedOpenApi.builder().group("api").pathsToMatch("/api/**").build() }
}

/** Registers the application's beans. Imported by [no.difi.meldingsutveksling.nhn.adapter.config.AppConfig]. */
class BeanRegistration :
    BeanRegistrarDsl({
        register(properties())
        register(security())
        register(integrations())
        register(services())
        register(handlers())
        register(openApi())
    })

private inline fun <reified T : Any> Environment.bindRequired(prefix: String): T =
    Binder.get(this).bind(prefix, T::class.java).orElseThrow {
        IllegalStateException("Configuration properties '$prefix' for ${T::class.simpleName} were not found.")
    }

private fun keystoreProperties(resourceLoader: ResourceLoader, config: KeystoreConfig): KeystoreProperties =
    KeystoreProperties()
        .setAlias(config.alias)
        .setPassword(config.password)
        .setType(config.type)
        .setPath(resourceLoader.getResource(config.path))
        .setLockProvider(config.lockProvider)

private fun tempFileResourceFactory(config: TempFileConfig): InMemoryWithTempFileFallbackResourceFactory =
    InMemoryWithTempFileFallbackResourceFactory(config.threshold, config.initialBufferSize, config.directory)

private fun openApiDefinition(): OpenAPI =
    OpenAPI()
        .info(Info().title("NHN Adapter API").description("API documentation for NHN Adapter").version("1.0.0"))
        .servers(
            listOf(
                Server().url("http://localhost:8082/nhn-adapter").description("localhost"),
                Server().url("https://test.eformidling.no/nhn-adapter").description("Test"),
                Server().url("https://eformidling.no/nhn-adapter").description("Production"),
            )
        )
        .components(
            Components()
                .addSecuritySchemes(
                    OPENAPI_SECURITY_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name(HttpHeaders.AUTHORIZATION)
                        .description("Bearer <Maskinportentoken>"),
                )
        )
        .addSecurityItem(SecurityRequirement().addList(OPENAPI_SECURITY_SCHEME))
