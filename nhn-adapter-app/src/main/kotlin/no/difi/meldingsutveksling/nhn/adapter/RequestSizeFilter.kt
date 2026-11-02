package no.difi.meldingsutveksling.nhn.adapter

import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * Rejects a request with a `Content-Length` above [maxRequestSize] before its body is read.
 *
 * A chunked request has no `Content-Length`. Its multipart body is limited by `spring.webflux.multipart` instead.
 */
fun requestSizeFilter(maxRequestSize: DataSize): HandlerFilterFunction<ServerResponse, ServerResponse> =
    HandlerFilterFunction { request, next ->
        val contentLength = request.headers().contentLength()
        if (contentLength.isPresent && contentLength.asLong > maxRequestSize.toBytes()) {
            Mono.error(
                MessageTooLargeException("The request exceeds the max size of ${maxRequestSize.toBytes()} bytes")
            )
        } else {
            next.handle(request)
        }
    }
