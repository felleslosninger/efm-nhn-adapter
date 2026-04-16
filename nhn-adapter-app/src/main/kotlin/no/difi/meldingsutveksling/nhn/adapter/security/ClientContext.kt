package no.difi.meldingsutveksling.nhn.adapter.security

import kotlinx.coroutines.reactor.awaitSingle
import no.difi.meldingsutveksling.domain.Iso6523
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.util.context.ContextView

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

class ClientContextFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {
    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> =
        next.handle(request).contextWrite { ctx ->
            AccessToken.getJwt()?.let { ctx.put("client", ClientContextImpl(it)) }
            ctx
        }
}

suspend fun getClientContext(): ClientContext =
    Mono.deferContextual { ctx: ContextView ->
            val clientContext: ClientContext = ctx.get("client")
            Mono.just(clientContext)
        }
        .awaitSingle()
