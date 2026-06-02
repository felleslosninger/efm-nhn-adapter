package no.difi.meldingsutveksling.nhn.adapter.security

import kotlinx.coroutines.reactor.awaitSingle
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.model.Claims
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

interface ClientContext {
    val clientId: String
    val orgNumber: String
    val onBehalfOfOrgNumber: String
    val consumer: Iso6523
    val supplier: Iso6523?
    val delegationSource: String?
    val scopes: Set<String>
}

data class ClientContextImpl(val jwt: Jwt) : ClientContext {
    override val clientId: String by lazy { jwt.getClientId() }

    override val orgNumber: String by lazy { supplier?.organizationIdentifier ?: onBehalfOfOrgNumber }

    override val onBehalfOfOrgNumber: String by lazy { consumer.organizationIdentifier }

    override val consumer: Iso6523 by lazy { jwt.getConsumer() }

    override val supplier: Iso6523? by lazy { jwt.getSupplier() }

    override val delegationSource: String? by lazy { jwt.getDelegationSource() }

    override val scopes: Set<String> by lazy { jwt.getScopes() }
}

suspend fun getClientContext(): ClientContext {
    val securityContext: SecurityContext = ReactiveSecurityContextHolder.getContext().awaitSingle()
    return ClientContextImpl(securityContext.getJwt()!!)
}

fun Jwt.getClientId(): String = getClaimAsString(Claims.CLIENT_ID)!!

fun Jwt.getConsumer(): Iso6523 = getIso6523(Claims.CONSUMER)!!

fun Jwt.getSupplier(): Iso6523? = getIso6523(Claims.SUPPLIER)

fun Jwt.getDelegationSource(): String? = getClaimAsString(Claims.DELEGATION_SOURCE)

fun Jwt.getScopes(): Set<String> = getClaimAsString(Claims.SCOPE).split(' ').toSet()

private fun Jwt.getIso6523(claim: String): Iso6523? {
    val claimAsMap = getClaimAsMap(claim)
    val id = claimAsMap?.get("ID")
    return if (id is String) Iso6523.parse(id) else null
}

fun SecurityContext.getJwt(): Jwt? {
    val authentication = this.authentication
    return if (authentication is JwtAuthenticationToken) authentication.token as Jwt else null
}
