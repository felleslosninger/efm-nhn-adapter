package no.difi.meldingsutveksling.nhn.adapter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import java.util.concurrent.atomic.AtomicLong
import no.difi.meldingsutveksling.nhn.adapter.errors.RequestTooLargeException
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.Part
import org.springframework.test.web.reactive.server.MockServerConfigurer
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Operators
import reactor.util.context.Context

private val MAX_REQUEST_SIZE = DataSize.ofKilobytes(1)
private const val CHUNK_SIZE = 100
private const val BOUNDARY = "chunked-boundary"

class RequestSizeFilterTest :
    DescribeSpec({
        // As main() does at startup
        beforeSpec { logDroppedRequestTooLargeErrorsAtDebug() }

        // Filtered as in RoutesConfiguration. The builder's filter() would apply the filters in the
        // opposite order. The handler reads the whole body, as OutHandler does, and answers with
        // the size of each multipart part. RequestBodyLimit wraps the server, as in the
        // application.
        val client =
            WebTestClient.bindToRouterFunction(
                    RouterFunctions.route()
                        .POST(ApiPaths.OUTGOING_MESSAGES) { request ->
                            request
                                .multipartData()
                                .flatMapIterable { it.values.flatten() }
                                .concatMap { part -> part.size().map { "${part.name()}=$it" } }
                                .collectList()
                                .flatMap { ServerResponse.ok().bodyValue(it.joinToString(",")) }
                        }
                        .build()
                        .filter(requestSizeFilter(MAX_REQUEST_SIZE))
                        .filter(nhnErrorFilter())
                )
                .apply<WebTestClient.RouterFunctionSpec>(
                    object : MockServerConfigurer {
                        override fun beforeServerCreated(builder: WebHttpHandlerBuilder) {
                            builder.httpHandlerDecorator(RequestBodyLimit(MAX_REQUEST_SIZE))
                        }
                    }
                )
                .build()

        fun post(size: Long) =
            client.post().uri(ApiPaths.OUTGOING_MESSAGES).bodyValue(ByteArray(size.toInt())).exchange()

        /** Sent in chunks of [CHUNK_SIZE] bytes, without a `Content-Length`. Counts the bytes taken from [body]. */
        fun postChunked(body: ByteArray, sent: AtomicLong = AtomicLong()) =
            client
                .post()
                .uri(ApiPaths.OUTGOING_MESSAGES)
                .contentType(MediaType.parseMediaType("multipart/form-data;boundary=$BOUNDARY"))
                .body(
                    chunked(body).doOnNext { sent.addAndGet(it.readableByteCount().toLong()) },
                    DataBuffer::class.java,
                )
                .exchange()

        describe("requestSizeFilter") {
            describe("with a Content-Length") {
                it("lets through a request of the max size") { post(MAX_REQUEST_SIZE.toBytes()).expectStatus().isOk }

                it("rejects a larger request with 413") {
                    post(MAX_REQUEST_SIZE.toBytes() + 1)
                        .expectStatus()
                        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                        .expectBody()
                        .jsonPath("$.details")
                        .isEqualTo("The request exceeds the max size of ${MAX_REQUEST_SIZE.toBytes()} bytes")
                }
            }

            describe("with a chunked request") {
                it("lets through a request below the max size") {
                    val body = multipart("first" to 300, "second" to 300)
                    check(body.size < MAX_REQUEST_SIZE.toBytes())

                    postChunked(body).expectStatus().isOk.expectBody<String>().isEqualTo("first=300,second=300")
                }

                it("rejects a larger request with 413, although each part is below the max size") {
                    val body = multipart("first" to 600, "second" to 600, "third" to 600)
                    val sent = AtomicLong()

                    postChunked(body, sent)
                        .expectStatus()
                        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                        .expectBody()
                        .jsonPath("$.details")
                        .isEqualTo("The request exceeds the max size of ${MAX_REQUEST_SIZE.toBytes()} bytes")

                    // Reading stops at the chunk that passes the max size
                    sent.get() shouldBeLessThanOrEqual MAX_REQUEST_SIZE.toBytes() + CHUNK_SIZE
                }
            }
        }

        describe("dropped errors") {
            // Where Reactor logs a dropped error by default. Other specs run at the same time, and
            // may log here too.
            val operatorsLog = LoggerFactory.getLogger(Operators::class.java) as Logger
            val events = ListAppender<ILoggingEvent>()

            beforeEach {
                events.list.clear()
                events.start()
                operatorsLog.addAppender(events)
            }

            afterEach { operatorsLog.detachAppender(events) }

            fun errors() =
                events.list
                    .filter { it.level == Level.ERROR }
                    .map { it.throwableProxy.className to it.throwableProxy.message }

            it("logs no error for a too large chunked request, which is already answered with 413") {
                // Spring's multipart parser hands the error from the body on twice, and Reactor
                // drops the second
                postChunked(multipart("first" to 600, "second" to 600))
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)

                errors()
                    .filter { (className) -> className == RequestTooLargeException::class.java.name }
                    .shouldBeEmpty()
            }

            it("still logs any other dropped error") {
                // Without a stack trace, which would only clutter the build log
                val dropped =
                    IllegalStateException("Dropped by RequestSizeFilterTest").apply { stackTrace = emptyArray() }

                Operators.onErrorDropped(dropped, Context.empty())

                errors() shouldContain (IllegalStateException::class.java.name to dropped.message)
            }
        }
    })

/** A multipart/form-data body with a part of the given size for each name. */
private fun multipart(vararg parts: Pair<String, Int>): ByteArray {
    val body =
        parts.joinToString("") { (name, size) ->
            "--$BOUNDARY\r\n" +
                "Content-Disposition: form-data; name=\"$name\"; filename=\"$name.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n" +
                "x".repeat(size) +
                "\r\n"
        } + "--$BOUNDARY--\r\n"
    return body.toByteArray()
}

private fun chunked(body: ByteArray): Flux<DataBuffer> =
    Flux.range(0, (body.size + CHUNK_SIZE - 1) / CHUNK_SIZE).map { index ->
        val from = index * CHUNK_SIZE
        DefaultDataBufferFactory.sharedInstance.wrap(body.copyOfRange(from, minOf(from + CHUNK_SIZE, body.size)))
    }

private fun Part.size(): Mono<Int> =
    DataBufferUtils.join(content()).map { buffer ->
        buffer.readableByteCount().also { DataBufferUtils.release(buffer) }
    }
