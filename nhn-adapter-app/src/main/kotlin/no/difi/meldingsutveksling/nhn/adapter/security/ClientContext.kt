package no.difi.meldingsutveksling.nhn.adapter.security

import kotlinx.coroutines.reactor.awaitSingle
import no.difi.meldingsutveksling.domain.Iso6523
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt

interface ClientContext {
    val orgNumber: String
    val onBehalfOfOrgNumber: String
    val consumer: Iso6523
    val supplier: Iso6523?
    val delegationSource: String?
}

data class ClientContextImpl(val jwt: Jwt) : ClientContext {
    override val orgNumber: String by lazy { supplier?.organizationIdentifier ?: onBehalfOfOrgNumber }

    override val onBehalfOfOrgNumber: String by lazy { consumer.organizationIdentifier }

    override val consumer: Iso6523 by lazy { AccessToken.getConsumer(jwt) }

    override val supplier: Iso6523? by lazy { AccessToken.getSupplier(jwt) }

    override val delegationSource: String? by lazy { AccessToken.getDelegationSource(jwt) }
}

suspend fun getClientContext(): ClientContext {
    val securityContext: SecurityContext = ReactiveSecurityContextHolder.getContext().awaitSingle()
    return ClientContextImpl(AccessToken.getJwt(securityContext)!!)
}
