package no.difi.meldingsutveksling.nhn.adapter.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.UUID
import kotlin.time.measureTimedValue
import no.difi.meldingsutveksling.nhn.adapter.config.MshTimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.config.NhnConfig
import no.difi.meldingsutveksling.nhn.adapter.config.TimeoutConfig
import no.difi.meldingsutveksling.nhn.adapter.isTimeout
import no.ks.fiks.helseid.Environment
import no.ks.fiks.nhn.ar.rest.AdresseregisteretException
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.MshException

/** How long the stubbed services wait before they answer, well beyond the timeouts in the tests. */
private const val SLOW_ANSWER_MILLIS = 5_000

private val SHORT = Duration.ofMillis(500)
private val LONG = Duration.ofSeconds(30)

/** Runs [call], which must fail with [T] caused by a timeout, well before the stubbed service answers. */
private suspend inline fun <reified T : Throwable> shouldTimeOut(call: suspend () -> Unit) {
    val (error, elapsed) = measureTimedValue { shouldThrow<T> { call() } }
    error.isTimeout() shouldBe true
    elapsed.inWholeMilliseconds shouldBeLessThan SLOW_ANSWER_MILLIS.toLong()
}

/** Each client gives up after its timeout, with an error that the error filter reports as 504. */
class TimeoutIT :
    DescribeSpec({
        val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        val rsaJwk = RSAKeyGenerator(2048).keyID("test-key").generate().toJSONString()

        fun helseIdConfig() =
            HelseIdConfiguration(
                Environment(wireMockServer.baseUrl(), wireMockServer.baseUrl()),
                "test-client-id",
                rsaJwk,
            )

        fun stubSlowAnswer(urlPattern: String) {
            wireMockServer.stubFor(
                any(urlPathMatching(urlPattern))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(SLOW_ANSWER_MILLIS))
            )
        }

        beforeSpec { wireMockServer.start() }

        afterSpec { wireMockServer.stop() }

        beforeEach {
            wireMockServer.resetAll()
            wireMockServer.stubHelseIdSts()
        }

        describe("MSH") {
            fun mshClient(timeout: MshTimeoutConfig, helseIdTimeout: TimeoutConfig = TimeoutConfig(LONG, LONG)) =
                IntegrationBeans.mshClient(
                    IntegrationBeans.mshInternalClient(
                        helseIdConfig(),
                        IntegrationBeans.helseIdClient(helseIdConfig(), helseIdTimeout),
                        wireMockServer.baseUrl(),
                        timeout,
                    )
                )

            it("gives up waiting for data after the read timeout") {
                stubSlowAnswer("/Messages/.*")
                val client = mshClient(MshTimeoutConfig(LONG, SHORT, LONG))

                shouldTimeOut<MshException> { client.getStatus(UUID.randomUUID()) }
            }

            it("gives up a call after the request timeout") {
                stubSlowAnswer("/Messages/.*")
                val client = mshClient(MshTimeoutConfig(LONG, LONG, SHORT))

                shouldTimeOut<MshException> { client.getStatus(UUID.randomUUID()) }
            }

            it("gives up waiting for a HelseID token after HelseID's read timeout") {
                wireMockServer.resetAll()
                wireMockServer.stubHelseIdSts(tokenDelayMillis = SLOW_ANSWER_MILLIS)
                val client = mshClient(MshTimeoutConfig(LONG, LONG, LONG), helseIdTimeout = TimeoutConfig(LONG, SHORT))

                shouldTimeOut<MshException> { client.getStatus(UUID.randomUUID()) }
            }

            it("gives up waiting for HelseID's OpenID configuration after HelseID's read timeout") {
                wireMockServer.resetAll()
                wireMockServer.stubFor(
                    get("/.well-known/openid-configuration")
                        .willReturn(aResponse().withStatus(200).withFixedDelay(SLOW_ANSWER_MILLIS))
                )
                val client = mshClient(MshTimeoutConfig(LONG, LONG, LONG), helseIdTimeout = TimeoutConfig(LONG, SHORT))

                shouldTimeOut<MshException> { client.getStatus(UUID.randomUUID()) }
            }
        }

        describe("Adresseregisteret") {
            it("gives up waiting for data after the read timeout") {
                stubSlowAnswer("/ar/.*")
                val client =
                    IntegrationBeans.arClient(
                        helseIdConfig(),
                        IntegrationBeans.helseIdClient(helseIdConfig(), TimeoutConfig(LONG, LONG)),
                        "${wireMockServer.baseUrl()}/ar",
                        TimeoutConfig(LONG, SHORT),
                    )

                shouldTimeOut<AdresseregisteretException> { client.lookupHerId(12345) }
            }
        }

        describe("Fastlegeregisteret") {
            it("gives up waiting for data after the read timeout") {
                stubSlowAnswer("/flr")
                val client =
                    IntegrationBeans.flrClient(
                        NhnConfig("${wireMockServer.baseUrl()}/flr", "user", "password", TimeoutConfig(LONG, SHORT))
                    )

                shouldTimeOut<FastlegeregisteretException> { client.getPatientGP("12345678901") }
            }
        }
    })
