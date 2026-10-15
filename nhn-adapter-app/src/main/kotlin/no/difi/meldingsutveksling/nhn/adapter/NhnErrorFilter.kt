package no.difi.meldingsutveksling.nhn.adapter

import java.util.Date
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
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

private const val CLIENT_ERROR = "Client error"
private const val UNEXPECTED_ERROR_CODE = "E7777"
private const val ADRESSEREGISTERET_ERROR_CODE = "E7778"
private const val FASTLEGEREGISTERET_ERROR_CODE = "E7779"

/** Translates exceptions thrown by the handlers into [ApiError] responses. */
fun nhnErrorFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> = HandlerFilterFunction { request, next ->
    next.handle(request).onErrorResume { request.toApiError(it).toServerResponse() }
}

private fun ServerRequest.toApiError(error: Throwable): ApiError =
    when (error) {
        is IllegalArgumentException,
        is DialogmeldingNotFoundException,
        is HerIdNotFoundException -> clientError(error.message ?: CLIENT_ERROR)
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
        is HttpException -> {
            logger.error("HttpException: ${error.message}")
            toApiError(
                HttpStatus.valueOf(error.status),
                FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                error.message,
            )
        }
        else -> {
            logger.error("Unexpected error: ${error.message}", error)
            toApiError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                tryLaterMessage(UNEXPECTED_ERROR_CODE),
            )
        }
    }

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
        timestamp = Date().toString(),
        status = status.value(),
        reason = status.reasonPhrase,
        errorCode = error.verdi,
        message = error.navn,
        details = details,
        path = path(),
        requestId = exchange().request.id,
    )

private fun ApiError.toServerResponse(): Mono<ServerResponse> = ServerResponse.status(status).bodyValue(this)
