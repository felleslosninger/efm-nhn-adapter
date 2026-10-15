package no.difi.meldingsutveksling.nhn.adapter

import java.time.Instant
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.DialogmeldingNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.model.ApiError
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.msh.HttpException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.util.pattern.PathPattern
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

private const val CLIENT_ERROR = "Client error"
private const val ACCESS_DENIED = "Access denied"
private const val UNEXPECTED_ERROR_CODE = "E7777"
private const val ADRESSEREGISTERET_ERROR_CODE = "E7778"
private const val FASTLEGEREGISTERET_ERROR_CODE = "E7779"
private const val MSH_ERROR_CODE = "E7780"

/** MSH answers these when the adapter itself is not authorized, e.g. a misconfigured HelseID client. */
private val MSH_AUTHORIZATION_ERRORS = setOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)

/** Translates exceptions thrown by the handlers into [ApiError] responses. */
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
        is IllegalArgumentException,
        is DialogmeldingNotFoundException -> clientError(error.message ?: CLIENT_ERROR)
        is AccessDeniedException -> {
            logger.warn("Access denied for ${path()}: ${error.message}")
            toApiError(HttpStatus.FORBIDDEN, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, ACCESS_DENIED)
        }
        is ApplicationReceiptException -> toApiError(HttpStatus.BAD_REQUEST, error.error, error.message)
        is AdresseregisteretApiException -> badGateway(error.errorCode)
        is AdresseregisteretException -> {
            logger.error(
                "Technical error occurred against Adresseregisteret for ${path()}. Logging cause.",
                error.cause,
            )
            badGateway(ADRESSEREGISTERET_ERROR_CODE)
        }
        is FastlegeregisteretException -> {
            logger.error(
                "Technical error occurred against Fastlegeregisteret for ${path()}. Logging cause.",
                error.cause,
            )
            badGateway(FASTLEGEREGISTERET_ERROR_CODE)
        }
        is HttpException -> mshError(error)
        else -> {
            logger.error("Unexpected error: ${error.message}", error)
            toApiError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                tryLaterMessage(UNEXPECTED_ERROR_CODE),
            )
        }
    }

/**
 * A client error from MSH is caused by the request (e.g. an unknown message id), so its status and message are passed
 * on. Anything else is a problem between the adapter and MSH and is reported as 502, without MSH's message.
 */
private fun ServerRequest.mshError(error: HttpException): ApiError {
    val clientErrorStatus =
        HttpStatus.resolve(error.status)?.takeIf { it.is4xxClientError && it !in MSH_AUTHORIZATION_ERRORS }
    if (clientErrorStatus != null) {
        logger.warn("MSH rejected the request for ${path()}: ${error.message}")
        return toApiError(clientErrorStatus, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, error.message)
    }
    logger.error("MSH failed for ${path()}: ${error.message}")
    return badGateway(MSH_ERROR_CODE)
}

private fun ServerRequest.matchesRoute(pattern: String): Boolean =
    (attribute(RouterFunctions.MATCHING_PATTERN_ATTRIBUTE).orElse(null) as? PathPattern)?.patternString == pattern

private fun ServerRequest.clientError(details: String): ApiError =
    toApiError(HttpStatus.BAD_REQUEST, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, details)

private fun ServerRequest.badGateway(errorCode: String?): ApiError =
    toApiError(HttpStatus.BAD_GATEWAY, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, tryLaterMessage(errorCode))

private fun tryLaterMessage(errorCode: String?) = "Not able to process, try later. ErrorCode: $errorCode"

private fun ServerRequest.toApiError(
    status: HttpStatus,
    error: FeilmeldingForApplikasjonskvittering,
    details: String? = null,
): ApiError =
    ApiError(
        timestamp = Instant.now().toString(),
        status = status.value(),
        reason = status.reasonPhrase,
        errorCode = error.verdi,
        message = error.navn,
        details = details,
        path = path(),
        requestId = exchange().request.id,
    )

private fun ApiError.toServerResponse(): Mono<ServerResponse> = ServerResponse.status(status).bodyValue(this)
