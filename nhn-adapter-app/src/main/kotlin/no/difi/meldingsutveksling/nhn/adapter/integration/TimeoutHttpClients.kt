package no.difi.meldingsutveksling.nhn.adapter.integration

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import java.time.OffsetDateTime
import no.difi.meldingsutveksling.nhn.adapter.config.MshTimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.config.TimeoutConfig
import no.ks.fiks.nhn.parseOffsetDateTimeOrNull
import org.apache.cxf.BusFactory
import org.apache.cxf.transport.http.HTTPConduitConfigurer
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout

/**
 * HTTP clients for the NHN client libraries, with the timeouts from the configuration. Left to themselves, the
 * libraries use their own defaults, and HelseID's never time out waiting for a response.
 */
internal object TimeoutHttpClients {
    /** For HelseID, which uses Apache HttpClient. */
    fun apache(timeout: TimeoutConfig): CloseableHttpClient =
        HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultConnectionConfig(
                        ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.of(timeout.connect))
                            .setSocketTimeout(Timeout.of(timeout.read))
                            .build()
                    )
                    .build()
            )
            .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(timeout.read)).build())
            .build()

    /**
     * For MSH. Apart from the timeouts, configured like the default client in msh-client's `MshInternalClient`, which
     * the library does not expose. Compare with it when upgrading msh-client.
     */
    fun msh(timeout: MshTimeoutConfig): KtorHttpClient =
        KtorHttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = timeout.connect.toMillis()
                socketTimeoutMillis = timeout.read.toMillis()
                requestTimeoutMillis = timeout.request.toMillis()
            }
            install(ContentNegotiation) {
                jackson {
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    registerModule(JavaTimeModule())
                    registerModule(
                        SimpleModule().addDeserializer(OffsetDateTime::class.java, OffsetDateTimeDeserializer)
                    )
                }
            }
        }

    /**
     * For Fastlegeregisteret: runs [create], which creates a CXF client, with a CXF bus that gives the client's HTTP
     * conduit the timeouts. fastlegeregisteret-client creates its CXF client itself, with the thread's default bus.
     */
    fun <T> withCxfTimeouts(timeout: TimeoutConfig, create: () -> T): T {
        val bus = BusFactory.newInstance().createBus()
        val conduitConfigurer = HTTPConduitConfigurer { _, _, conduit ->
            conduit.client =
                HTTPClientPolicy().apply {
                    connectionTimeout = timeout.connect.toMillis()
                    receiveTimeout = timeout.read.toMillis()
                }
        }
        bus.setExtension(conduitConfigurer, HTTPConduitConfigurer::class.java)

        val previousBus = BusFactory.getAndSetThreadDefaultBus(bus)
        try {
            return create()
        } finally {
            BusFactory.setThreadDefaultBus(previousBus)
        }
    }

    private object OffsetDateTimeDeserializer : JsonDeserializer<OffsetDateTime?>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): OffsetDateTime? =
            parser.text.parseOffsetDateTimeOrNull()
    }
}
