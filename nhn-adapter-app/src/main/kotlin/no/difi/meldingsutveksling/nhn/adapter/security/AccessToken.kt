package no.difi.meldingsutveksling.nhn.adapter.security

import no.difi.meldingsutveksling.domain.Iso6523
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

object AccessToken {

    fun getConsumer(jwt: Jwt): Iso6523 {
        return getIso6523(jwt, Claims.consumer)!!
    }

    fun getSupplier(jwt: Jwt): Iso6523? {
        return getIso6523(jwt, Claims.supplier)
    }

    fun getDelegationSource(jwt: Jwt): String? {
        return jwt.getClaimAsString(Claims.delegationSource)
    }

    private fun getIso6523(jwt: Jwt, claim: String): Iso6523? {
        val claimAsMap = jwt.getClaimAsMap(claim)
        val id = claimAsMap?.get("ID")
        return if (id is String) Iso6523.parse(id) else null
    }

    fun getJwt(): Jwt? {
        val authentication = SecurityContextHolder.getContext().authentication
        return if (authentication is JwtAuthenticationToken) authentication.token as Jwt else null
    }
}
