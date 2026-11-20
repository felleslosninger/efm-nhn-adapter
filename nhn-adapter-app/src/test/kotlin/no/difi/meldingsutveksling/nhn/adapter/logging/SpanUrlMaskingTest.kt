package no.difi.meldingsutveksling.nhn.adapter.logging

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.net.Socket
import reactor.netty.http.server.HttpServer

private const val FNR = "16822449879"
private val HTTP_ROUTE: AttributeKey<String> = AttributeKey.stringKey("http.route")

/**
 * An SDK span stands in for the server span of the OpenTelemetry Java agent, with the attributes the agent sets. That
 * the agent's span is current while Reactor Netty calls [SpanUrlMasking] is not shown here, only by running the
 * adapter with the agent (see README).
 */
class SpanUrlMaskingTest :
    DescribeSpec({
        val exporter = InMemorySpanExporter.create()
        val tracer =
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()
                .get(SpanUrlMaskingTest::class.java.name)

        /** A server span with the attributes the agent sets from [requestTarget], e.g. `/a/b?c=d`. */
        fun serverSpan(requestTarget: String): Span =
            tracer
                .spanBuilder("GET")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(URL_PATH, requestTarget.substringBefore('?'))
                .apply { if ('?' in requestTarget) setAttribute(URL_QUERY, requestTarget.substringAfter('?')) }
                .setAttribute(HTTP_ROUTE, "/api/lookup/{identifier}")
                .startSpan()

        fun exported(span: Span): SpanData {
            span.end()
            return exporter.finishedSpanItems.single { it.spanId == span.spanContext.spanId }
        }

        beforeEach { exporter.reset() }

        describe("maskFnrInUrl") {
            fun masked(requestTarget: String): SpanData =
                exported(serverSpan(requestTarget).apply { maskFnrInUrl(requestTarget) })

            it("masks the fødselsnummer in the path of a lookup") {
                val span = masked("/nhn-adapter/api/lookup/fastlege-for:$FNR")

                span.attributes.get(URL_PATH) shouldBe "/nhn-adapter/api/lookup/fastlege-for:***********"
                span.attributes.asMap().values.forEach { it.toString().contains(FNR) shouldBe false }
            }

            it("masks the fødselsnummer in the query") {
                val span = masked("/nhn-adapter/api/lookup/her-id:8143143?pasient=$FNR")

                span.attributes.get(URL_PATH) shouldBe "/nhn-adapter/api/lookup/her-id:8143143"
                span.attributes.get(URL_QUERY) shouldBe "pasient=***********"
            }

            it("masks the path of a request target in absolute form") {
                val span = serverSpan("/api/lookup/fastlege-for:$FNR")
                span.maskFnrInUrl("http://adapter:8080/api/lookup/fastlege-for:$FNR")

                exported(span).attributes.get(URL_PATH) shouldBe "/api/lookup/fastlege-for:***********"
            }

            it("leaves the attributes of a request without a fødselsnummer") {
                val span = masked("/nhn-adapter/api/lookup/her-id:8143143")

                span.attributes.get(URL_PATH) shouldBe "/nhn-adapter/api/lookup/her-id:8143143"
                span.attributes.get(URL_QUERY).shouldBeNull()
                span.attributes.get(HTTP_ROUTE) shouldBe "/api/lookup/{identifier}"
            }
        }

        describe("on a Reactor Netty server") {
            var current: Span = Span.getInvalid()
            val server =
                SpanUrlMasking { current }
                    .apply(HttpServer.create().host("localhost").port(0))
                    .handle { _, response -> response.status(404).send() }
                    .bindNow()

            afterSpec { server.disposeNow() }

            /** Sends [requestTarget] as it is, so that also a target that no HTTP client would send can be tested. */
            fun get(requestTarget: String): String =
                Socket("localhost", server.port()).use { socket ->
                    socket
                        .getOutputStream()
                        .write("GET $requestTarget HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    socket.getInputStream().bufferedReader().readLine()
                }

            listOf(
                    // Outside spring.webflux.base-path
                    "/api/lookup/fastlege-for:$FNR",
                    // Not a valid URI, so Spring would answer 400 before any of its own handlers
                    "/nhn-adapter/api/lookup/fastlege-for:$FNR%",
                    "/nhn-adapter/api/lookup/fastlege-for:{$FNR}",
                )
                .forEach { requestTarget ->
                    it("masks the fødselsnummer in $requestTarget before the request is handled") {
                        current = serverSpan(requestTarget)

                        get(requestTarget) shouldStartWith "HTTP/1.1 404"

                        exported(current).attributes.get(URL_PATH)?.contains(FNR) shouldBe false
                    }
                }
        }
    })
