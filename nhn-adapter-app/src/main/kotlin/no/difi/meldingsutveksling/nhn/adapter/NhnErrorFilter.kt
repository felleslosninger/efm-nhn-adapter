package no.difi.meldingsutveksling.nhn.adapter

import no.difi.meldingsutveksling.nhn.adapter.crypto.DecryptionException
import no.difi.meldingsutveksling.nhn.adapter.crypto.InvalidSignatureException
import no.difi.meldingsutveksling.nhn.adapter.handlers.HerIdNotFound
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
                request.toApiError(status = HttpStatus.BAD_REQUEST, it.message ?: "Client error")
            is HerIdNotFound -> {
                when (basePath) {
                    "/arlookup" -> request.toApiError(HttpStatus.NOT_FOUND, "HerId is not found")
                    else -> request.toApiError(HttpStatus.BAD_REQUEST)
                }
            }
            is AdresseregisteretApiException -> {
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: ${it.errorCode}",
                )
            }
            is AdresseregisteretException -> {
                logger.error(
                    "Technical error occured against AddressRegisteret for ${request.path()}. Logging cause. ",
                    it.cause,
                )
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: E7778",
                )
            }
            is FastlegeregisteretException -> {
                logger.error(
                    "Technical error occured against Fastlegeregisteret for ${request.path()}. Logging cause. ",
                    it.cause,
                )
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: E7779",
                )
            }
            is DecryptionException -> {
                logger.error("Unable to decrypt message", it as Throwable)
                request.toApiError(HttpStatus.BAD_REQUEST, "Unable to decrypt message")
            }
            is HttpException -> {
                request.toApiError(HttpStatus.valueOf(it.status), it.message!!)
            }
            is InvalidSignatureException -> {
                request.toApiError(status = HttpStatus.UNAUTHORIZED, it.message ?: "Signature verification failed")
            }
            else -> {
                logger.error("Unexpected error: ${it.message}", it)
                request.toApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: E7777",
                )
            }
        }.toServerResponse()
    }
}
