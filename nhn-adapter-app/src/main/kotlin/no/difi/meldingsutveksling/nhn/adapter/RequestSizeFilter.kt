package no.difi.meldingsutveksling.nhn.adapter

import java.util.concurrent.atomic.AtomicBoolean
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.RequestTooLargeException
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.HttpHandlerDecoratorFactory
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono

/**
 * Rejects a request with a `Content-Length` above [maxRequestSize] before its body is read.
 *
 * A chunked request has no `Content-Length`. Its body is counted by [RequestBodyLimit] instead.
 */
fun requestSizeFilter(maxRequestSize: DataSize): HandlerFilterFunction<ServerResponse, ServerResponse> =
    HandlerFilterFunction { request, next ->
        val contentLength = request.headers().contentLength()
        if (contentLength.isPresent && contentLength.asLong > maxRequestSize.toBytes()) {
            Mono.error(RequestTooLargeException(maxRequestSize.toBytes()))
        } else {
            next.handle(request)
        }
    }

/**
 * Counts the body of each request as it arrives, so that reading it fails with a [RequestTooLargeException] as soon as
 * it passes [maxRequestSize]. This limits a chunked request, which has no `Content-Length` for [requestSizeFilter] to
 * check. The handler sees the error, e.g. from `multipartData()`, before more than one network chunk above the limit
 * is read.
 *
 * The request is decorated before Spring creates the exchange, so that the exchange reads its multipart data from the
 * counted body, and still deletes the temporary files of the parts when the request is done.
 *
 * See [logDroppedRequestTooLargeErrorsAtDebug] for the error that Reactor logs for each request that is too large.
 */
class RequestBodyLimit(private val maxRequestSize: DataSize) : HttpHandlerDecoratorFactory {
    override fun apply(handler: HttpHandler): HttpHandler = HttpHandler { request, response ->
        val limited =
            object : ServerHttpRequestDecorator(request) {
                override fun getBody(): Flux<DataBuffer> = super.getBody().limitedTo(maxRequestSize)
            }
        handler.handle(limited, response)
    }
}

private fun Flux<DataBuffer>.limitedTo(maxSize: DataSize): Flux<DataBuffer> =
    Flux.defer {
        var received = 0L
        handle { buffer, sink ->
            received += buffer.readableByteCount()
            if (received > maxSize.toBytes()) {
                DataBufferUtils.release(buffer)
                sink.error(RequestTooLargeException(maxSize.toBytes()))
            } else {
                sink.next(buffer)
            }
        }
    }

private val logger = KotlinLogging.logger {}

/** Where Reactor logs a dropped error when no hook is installed. */
private val reactorDroppedErrorLogger = KotlinLogging.logger("reactor.core.publisher.Operators")

private val droppedErrorHookInstalled = AtomicBoolean()

/**
 * Spring's multipart parser passes an error from the body on to both the part being read and the request. Reactor
 * drops the second copy, and by default logs it as an ERROR, once for every request that [RequestBodyLimit] rejects.
 * The first copy is answered with 413, so a dropped [RequestTooLargeException] is only logged at debug. Any other
 * dropped error is logged as Reactor does by default.
 *
 * Reactor's hooks are global to the JVM and add up, so this is called once at startup, and later calls do nothing.
 * `Hooks.resetOnErrorDropped()` removes it for good.
 */
fun logDroppedRequestTooLargeErrorsAtDebug() {
    if (droppedErrorHookInstalled.compareAndSet(false, true)) {
        Hooks.onErrorDropped { error ->
            if (error is RequestTooLargeException) {
                logger.debug { "Dropped a copy of an error already answered with 413: ${error.message}" }
            } else {
                reactorDroppedErrorLogger.error(error) { "Operator called default onErrorDropped" }
            }
        }
    }
}
