package no.difi.meldingsutveksling.nhn.adapter.security

import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.ACCESS_DENIED
import no.difi.meldingsutveksling.nhn.adapter.model.ApiError
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.KxJson
import no.difi.meldingsutveksling.nhn.adapter.toApiError
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

private const val AUTHENTICATION_REQUIRED = "A valid access token is required"

/**
 * Answers the requests Spring Security rejects with an [ApiError], like the ones the handlers answer. The exception
 * message, e.g. why a token is invalid, comes from the JWT library and is only logged. The `WWW-Authenticate` header
 * gives the error code from RFC 6750, without a description.
 */
object ApiErrorSecurityHandlers {
    val authenticationEntryPoint = ServerAuthenticationEntryPoint { exchange, e ->
        logger.info("Authentication failed for request ${exchange.request.id}: ${e.message}")
        val error = (e as? OAuth2AuthenticationException)?.error?.errorCode
        exchange.writeApiError(HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED, bearerChallenge(error))
    }

    val accessDeniedHandler = ServerAccessDeniedHandler { exchange, e ->
        logger.info("Access denied for request ${exchange.request.id}: ${e.message}")
        exchange.writeApiError(HttpStatus.FORBIDDEN, ACCESS_DENIED, bearerChallenge("insufficient_scope"))
    }

    private fun bearerChallenge(error: String?) = if (error == null) "Bearer" else "Bearer error=\"$error\""

    private fun ServerWebExchange.writeApiError(
        status: HttpStatus,
        details: String,
        wwwAuthenticate: String,
    ): Mono<Void> {
        val apiError = toApiError(status, FeilmeldingForApplikasjonskvittering.ANNEN_FEIL, details)
        val body = KxJson.encode(apiError, ApiError.serializer()).toByteArray()
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.set(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate)
        return response.writeWith(Mono.fromSupplier { response.bufferFactory().wrap(body) })
    }
}
