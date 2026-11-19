package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.netty.http.server.HttpServer

private val MAX_SIZE = DataSize.ofMegabytes(1)
private const val BOUNDARY = "over-http"
private val TIMEOUT = Duration.ofSeconds(30)

/** Data sent per write by the clients */
private const val CHUNK = 64 * 1024

/** What Netty may have read past the max size when the body is rejected: one network read */
private const val READ_AHEAD = 64 * 1024

/**
 * A too large chunked request over a real connection to Reactor Netty, the server the adapter runs on, rather than the
 * mock transport of WebTestClient. The body is 16 times the max size, so the client is still sending when the adapter
 * answers.
 */
class RequestBodyLimitOverHttpTest :
    DescribeSpec({
        // The bytes that got past RequestBodyLimit to the exchange
        val received = AtomicLong()

        val router =
            RouterFunctions.route()
                .POST(ApiPaths.OUTGOING_MESSAGES) { request ->
                    request.multipartData().flatMap { ServerResponse.ok().bodyValue("Received") }
                }
                .build()
                // Filtered as in RoutesConfiguration
                .filter(requestSizeFilter(MAX_SIZE))
                .filter(nhnErrorFilter())
        val counting = RouterFunctions.toHttpHandler(router).countingBodyInto(received)
        // As the application's WebHttpHandlerBuilder does with the RequestBodyLimit bean
        val server =
            HttpServer.create()
                .host("localhost")
                .port(0)
                .handle(ReactorHttpHandlerAdapter(RequestBodyLimit(MAX_SIZE).apply(counting)))
                .bindNow()
        val url = "http://localhost:${server.port()}${ApiPaths.OUTGOING_MESSAGES}"
        val contentType = "multipart/form-data;boundary=$BOUNDARY"
        val tooLargeBody = partHeader + ByteArray(16 * MAX_SIZE.toBytes().toInt())

        beforeEach { received.set(0) }

        afterSpec { server.disposeNow() }

        fun shouldBeRejected(status: Int, body: String) {
            status shouldBe HttpStatus.CONTENT_TOO_LARGE.value()
            body shouldContain "The request exceeds the max size of ${MAX_SIZE.toBytes()} bytes"
            received.get() shouldBeLessThanOrEqual MAX_SIZE.toBytes() + READ_AHEAD
        }

        describe("a chunked request above the max size") {
            it("is answered with 413 to a WebClient") {
                val (status, body) =
                    WebClient.create()
                        .post()
                        .uri(url)
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(BodyInserters.fromDataBuffers(chunked(tooLargeBody)))
                        .exchangeToMono { response ->
                            response.bodyToMono(String::class.java).map { response.statusCode().value() to it }
                        }
                        .block(TIMEOUT)!!

                shouldBeRejected(status, body)
            }

            it("is answered with 413 to the JDK HttpClient, which sends the whole body before it reads the answer") {
                // Netty reads the rest of the body and throws it away, so that the client gets the
                // answer
                val response =
                    HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build()
                        .send(
                            HttpRequest.newBuilder(URI(url))
                                .header("Content-Type", contentType)
                                .timeout(TIMEOUT)
                                // Of unknown length, so the request is chunked
                                .POST(HttpRequest.BodyPublishers.ofInputStream { tooLargeBody.inputStream() })
                                .build(),
                            HttpResponse.BodyHandlers.ofString(),
                        )

                shouldBeRejected(response.statusCode(), response.body())
            }
        }
    })

/** The start of a multipart body with one file part, which the rest of the body is the content of. */
private val partHeader =
    ("--$BOUNDARY\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"file.bin\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n")
        .toByteArray()

private fun chunked(body: ByteArray): Flux<DataBuffer> =
    Flux.range(0, (body.size + CHUNK - 1) / CHUNK).map { index ->
        val from = index * CHUNK
        DefaultDataBufferFactory.sharedInstance.wrap(body.copyOfRange(from, minOf(from + CHUNK, body.size)))
    }

/** Counts the bytes of the request bodies that this handler reads. */
private fun HttpHandler.countingBodyInto(counter: AtomicLong): HttpHandler = HttpHandler { request, response ->
    val counted =
        object : ServerHttpRequestDecorator(request) {
            override fun getBody(): Flux<DataBuffer> =
                super.getBody().doOnNext { counter.addAndGet(it.readableByteCount().toLong()) }
        }
    handle(counted, response)
}
