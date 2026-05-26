package no.difi.meldingsutveksling.nhn.adapter

import no.difi.meldingsutveksling.nhn.adapter.handlers.DialogmeldingNotFound
import no.difi.meldingsutveksling.nhn.adapter.handlers.HerIdNotFound
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.msh.HttpException
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.ServerResponse

fun nhnErrorFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> = HandlerFilterFunction { request, next ->
    next.handle(request).onErrorResume {
        val basePath = request.path().substringBeforeLast("/")
        when (it) {
            is IllegalArgumentException ->
                request.toApiError(
                    HttpStatus.BAD_REQUEST,
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    it.message ?: "Client error",
                )
            is DialogmeldingNotFound -> {
                request.toApiError(
                    HttpStatus.BAD_REQUEST,
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    it.message ?: "Client error",
                )
            }
            is HerIdNotFound -> {
                when (basePath) {
                    "/lookup" ->
                        request.toApiError(
                            HttpStatus.NOT_FOUND,
                            FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                            "HerId is not found",
                        )
                    else ->
                        request.toApiError(
                            HttpStatus.BAD_REQUEST,
                            FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                            it.message ?: "Client error",
                        )
                }
            }
            is ApplicationReceiptException -> {
                request.toApiError(status = HttpStatus.BAD_REQUEST, it.error, it.message)
            }
            is AdresseregisteretApiException -> {
                request.toApiError(
                    HttpStatus.BAD_GATEWAY,
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    "Not able to process, try later. ErrorCode: ${it.errorCode}",
                )
            }
            is AdresseregisteretException -> {
                logger.error(
                    "Technical error occurred against AddressRegisteret for ${request.path()}. Logging cause. ",
                    it.cause,
                )
                request.toApiError(
                    HttpStatus.BAD_GATEWAY,
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    "Not able to process, try later. ErrorCode: E7778",
                )
            }
            is FastlegeregisteretException -> {
                logger.error(
                    "Technical error occurred against Fastlegeregisteret for ${request.path()}. Logging cause. ",
                    it.cause,
                )
                request.toApiError(
                    HttpStatus.BAD_GATEWAY,
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    "Not able to process, try later. ErrorCode: E7779",
                )
            }
            is HttpException -> {
                logger.error("HttpException: ${it.message}")
                request.toApiError(
                    HttpStatus.valueOf(it.status),
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    it.message!!,
                )
            }
            else -> {
                logger.error("Unexpected error: ${it.message}", it)
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
                    "Not able to process, try later. ErrorCode: E7777",
                )
            }
        }.toServerResponse()
    }
}
