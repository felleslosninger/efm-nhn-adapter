package no.difi.meldingsutveksling.nhn.adapter.security

import no.difi.meldingsutveksling.nhn.adapter.config.HelseId
import no.difi.meldingsutveksling.nhn.adapter.config.SecurityConfig
import no.ks.fiks.helseid.Environment
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import org.springframework.security.config.Customizer
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver
import org.springframework.security.web.server.SecurityWebFilterChain

object SecurityBeans {
    private val PUBLIC_PATHS =
        arrayOf(
            "/health/**",
            "/prometheus",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/v3/api-docs/**",
        )

    private val API_AUTHORITIES = arrayOf("SCOPE_eformidling:dph", "SCOPE_move/dph.read")

    fun securityFilterChain(http: ServerHttpSecurity, config: SecurityConfig): SecurityWebFilterChain =
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange { exchange ->
                exchange
                    .pathMatchers(*PUBLIC_PATHS)
                    .permitAll()
                    .pathMatchers("/api/**")
                    .hasAnyAuthority(*API_AUTHORITIES)
                    .anyExchange()
                    .authenticated()
            }
            .headers { it.frameOptions(Customizer.withDefaults()) }
            .oauth2ResourceServer {
                it.authenticationManagerResolver(
                    JwtIssuerReactiveAuthenticationManagerResolver.fromTrustedIssuers(config.trustedIssuers)
                )
            }
            .build()

    fun helseIdConfiguration(helseId: HelseId) =
        HelseIdConfiguration(Environment(helseId.issuer, helseId.audience), helseId.clientId, helseId.privateKey)
}
