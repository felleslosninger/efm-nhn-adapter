package no.difi.meldingsutveksling.nhn.adapter

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.opentelemetry.api.trace.Span
import java.io.InterruptedIOException
import java.net.http.HttpTimeoutException
import java.time.Instant
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.AdapterBusyException
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.errors.UploadTimeoutException
import no.difi.meldingsutveksling.nhn.adapter.model.ApiError
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.ar.rest.AdresseregisteretException
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.msh.HttpException
import no.ks.fiks.nhn.msh.MshException
import org.springframework.core.codec.DecodingException
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.pattern.PathPattern
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

private const val CLIENT_ERROR = "Client error"
internal const val ACCESS_DENIED = "Access denied"
private const val REJECTED_BY_MSH = "The request was rejected by MSH"
private const val TOO_LARGE_BODY = "The request body is too large"
private const val INVALID_BODY = "The request body could not be read"
private const val UNEXPECTED_ERROR_CODE = "E7777"
private const val ADRESSEREGISTERET_ERROR_CODE = "E7778"
private const val FASTLEGEREGISTERET_ERROR_CODE = "E7779"
private const val MSH_ERROR_CODE = "E7780"
private const val ADAPTER_BUSY_ERROR_CODE = "E7781"

/** MSH answers these when the adapter itself is not authorized, e.g. a misconfigured HelseID client. */
private val MSH_AUTHORIZATION_ERRORS = setOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)

/**
 * Translates exceptions thrown by the handlers into [ApiError] responses.
 *
 * Only messages written by the adapter itself reach the client, like the message of a [ClientInputException]. Messages
 * from libraries and from the services behind the adapter may reveal internals, so they are only logged.
 */
fun nhnErrorFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> = HandlerFilterFunction { request, next ->
    next.handle(request).onErrorResume { request.toApiError(it).toServerResponse() }
}

private fun ServerRequest.toApiError(error: Throwable): ApiError =
    when (error) {
        // Unknown HER-id: 404 when it is the looked-up resource, 400 (bad input) elsewhere.
        is HerIdNotFoundException ->
            if (matchesRoute(ApiPaths.LOOKUP)) {
                toApiError(HttpStatus.NOT_FOUND, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, error.message)
            } else {
                clientError(error.message ?: CLIENT_ERROR)
            }
        is ClientInputException -> {
            logger.warn("Invalid input for ${route()}: ${error.message}", error.cause)
            clientError(error.message ?: CLIENT_ERROR)
        }
        is MessageTooLargeException ->
            toApiError(HttpStatus.CONTENT_TOO_LARGE, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, error.message)
        is UploadTimeoutException -> {
            logger.warn("Upload timed out for ${route()}: ${error.message}")
            toApiError(HttpStatus.REQUEST_TIMEOUT, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, error.message)
        }
        is AccessDeniedException -> {
            logger.warn("Access denied for ${route()}: ${error.message}")
            toApiError(HttpStatus.FORBIDDEN, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, ACCESS_DENIED)
        }
        // The message may come from JAXB or the schema validation. The error code says what is
        // wrong.
        is ApplicationReceiptException -> {
            logger.warn("Invalid message for ${route()}: ${error.message}", error.cause)
            toApiError(HttpStatus.BAD_REQUEST, error.error)
        }
        is AdresseregisteretException -> serviceError("Adresseregisteret", ADRESSEREGISTERET_ERROR_CODE, error)
        is FastlegeregisteretException -> serviceError("Fastlegeregisteret", FASTLEGEREGISTERET_ERROR_CODE, error)
        is HttpException -> mshError(error)
        // Network errors and timeouts against MSH or HelseID, which msh-client wraps in an
        // MshException
        is MshException -> serviceError("MSH", MSH_ERROR_CODE, error)
        is InvalidMshDataException -> {
            logger.error("Invalid data from MSH for ${route()}", error)
            badGateway(MSH_ERROR_CODE)
        }
        is AdapterBusyException -> {
            logger.warn("Too busy for ${route()}: ${error.message}")
            toApiError(
                HttpStatus.SERVICE_UNAVAILABLE,
                FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                tryLaterMessage(ADAPTER_BUSY_ERROR_CODE),
                ADAPTER_BUSY_ERROR_CODE,
            )
        }
        else -> multipartError(error) ?: unexpectedError(error)
    }

/**
 * A request body that breaks the limits in `spring.webflux.multipart`, which Spring reports while reading the parts.
 * Spring's message is only logged.
 */
private fun ServerRequest.multipartError(error: Throwable): ApiError? =
    when {
        error.hasCause<DataBufferLimitException>() -> {
            logger.warn("Too large request body for ${route()}: ${error.message}")
            toApiError(HttpStatus.CONTENT_TOO_LARGE, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, TOO_LARGE_BODY)
        }
        error.hasCause<DecodingException>() -> {
            logger.warn("Invalid request body for ${route()}: ${error.message}", error)
            clientError(INVALID_BODY)
        }
        else -> null
    }

private fun ServerRequest.unexpectedError(error: Throwable): ApiError {
    logger.error("Unexpected error: ${error.message}", error)
    return toApiError(
        HttpStatus.INTERNAL_SERVER_ERROR,
        FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
        tryLaterMessage(UNEXPECTED_ERROR_CODE),
        UNEXPECTED_ERROR_CODE,
    )
}

/** A service behind the adapter that did not answer in time is reported as 504, any other failure as 502. */
private fun ServerRequest.serviceError(service: String, technicalCode: String, error: Throwable): ApiError =
    if (error.isTimeout()) {
        logger.error("Timeout against $service for ${route()}", error)
        toApiError(
            HttpStatus.GATEWAY_TIMEOUT,
            FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
            tryLaterMessage(technicalCode),
            technicalCode,
        )
    } else {
        logger.error("Technical error occurred against $service for ${route()}", error)
        badGateway(technicalCode)
    }

/**
 * Whether the error is caused by a timeout in one of the HTTP clients. A socket timeout is an
 * [InterruptedIOException], like Apache HttpClient's connect timeout. The JDK client (used by CXF) and ktor (used by
 * MSH) have their own types.
 */
internal fun Throwable.isTimeout(): Boolean =
    causes().any {
        it is InterruptedIOException ||
            it is HttpTimeoutException ||
            it is HttpRequestTimeoutException ||
            it is ConnectTimeoutException
    }

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean = causes().any { it is T }

/** This error and its causes. Stops at a cause that refers to itself, or one already seen. */
private fun Throwable.causes(): Sequence<Throwable> {
    val seen = mutableSetOf<Throwable>()
    return generateSequence(this) { it.cause }.takeWhile { seen.add(it) }
}

/**
 * A client error from MSH is caused by the request (e.g. an unknown message id), so its status is passed on. Anything
 * else is a problem between the adapter and MSH and is reported as 502. MSH's message is only logged.
 */
private fun ServerRequest.mshError(error: HttpException): ApiError {
    val clientErrorStatus =
        HttpStatus.resolve(error.status)?.takeIf { it.is4xxClientError && it !in MSH_AUTHORIZATION_ERRORS }
    if (clientErrorStatus != null) {
        logger.warn("MSH rejected the request for ${route()}: ${error.message}")
        return toApiError(clientErrorStatus, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, REJECTED_BY_MSH)
    }
    logger.error("MSH failed for ${route()}: ${error.message}")
    return badGateway(MSH_ERROR_CODE)
}

private fun ServerRequest.matchingPattern(): String? =
    (attribute(RouterFunctions.MATCHING_PATTERN_ATTRIBUTE).orElse(null) as? PathPattern)?.patternString

private fun ServerRequest.matchesRoute(pattern: String): Boolean = matchingPattern() == pattern

/**
 * The route for log messages, e.g. `GET /api/lookup/{identifier}`. The raw path is not logged, since the path
 * variables may be personal data, like the fødselsnummer in `/api/lookup/fastlege-for:<fnr>`.
 */
private fun ServerRequest.route(): String = "${method()} ${matchingPattern() ?: "<unknown route>"}"

private fun ServerRequest.clientError(details: String): ApiError =
    toApiError(HttpStatus.BAD_REQUEST, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, details)

private fun ServerRequest.badGateway(technicalCode: String): ApiError =
    toApiError(
        HttpStatus.BAD_GATEWAY,
        FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
        tryLaterMessage(technicalCode),
        technicalCode,
    )

/** The code is also in the text, for clients that do not read [ApiError.technicalCode]. */
private fun tryLaterMessage(technicalCode: String) = "Not able to process, try later. ErrorCode: $technicalCode"

private fun ServerRequest.toApiError(
    status: HttpStatus,
    error: FeilmeldingForApplikasjonskvittering,
    details: String? = null,
    technicalCode: String? = null,
): ApiError = exchange().toApiError(status, error, details, technicalCode)

/** Also used for the errors from Spring Security, which reject the request before it reaches the handlers. */
internal fun ServerWebExchange.toApiError(
    status: HttpStatus,
    error: FeilmeldingForApplikasjonskvittering,
    details: String? = null,
    technicalCode: String? = null,
): ApiError =
    ApiError(
        timestamp = Instant.now().toString(),
        status = status.value(),
        reason = status.reasonPhrase,
        errorCode = error.verdi,
        message = error.navn,
        details = details,
        technicalCode = technicalCode,
        path = request.path.pathWithinApplication().value(),
        requestId = request.id,
        traceId = currentTraceId(),
    )

/** Set by the OpenTelemetry Java agent, which is injected when the adapter is deployed. Without it there is none. */
private fun currentTraceId(): String? = Span.current().spanContext.takeIf { it.isValid }?.traceId

private fun ApiError.toServerResponse(): Mono<ServerResponse> = ServerResponse.status(status).bodyValue(this)
