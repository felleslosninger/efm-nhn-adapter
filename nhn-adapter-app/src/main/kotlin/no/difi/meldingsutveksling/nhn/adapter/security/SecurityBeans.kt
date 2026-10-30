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

    private const val API_AUTHORITY = "SCOPE_eformidling:dph"

    fun securityFilterChain(http: ServerHttpSecurity, config: SecurityConfig): SecurityWebFilterChain =
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange { exchange ->
                exchange
                    .pathMatchers(*PUBLIC_PATHS)
                    .permitAll()
                    .pathMatchers("/api/**")
                    .hasAuthority(API_AUTHORITY)
                    .anyExchange()
                    .authenticated()
            }
            .headers { it.frameOptions(Customizer.withDefaults()) }
            .exceptionHandling {
                it.authenticationEntryPoint(ApiErrorSecurityHandlers.authenticationEntryPoint)
                    .accessDeniedHandler(ApiErrorSecurityHandlers.accessDeniedHandler)
            }
            // Invalid tokens are handled here, not by exceptionHandling
            .oauth2ResourceServer {
                it.authenticationManagerResolver(
                        JwtIssuerReactiveAuthenticationManagerResolver.fromTrustedIssuers(config.trustedIssuers)
                    )
                    .authenticationEntryPoint(ApiErrorSecurityHandlers.authenticationEntryPoint)
                    .accessDeniedHandler(ApiErrorSecurityHandlers.accessDeniedHandler)
            }
            .build()

    fun helseIdConfiguration(helseId: HelseId) =
        HelseIdConfiguration(Environment(helseId.issuer, helseId.audience), helseId.clientId, helseId.privateKey)
}
