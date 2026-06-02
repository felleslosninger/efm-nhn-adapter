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
    fun securityFilterChain(http: ServerHttpSecurity, config: SecurityConfig): SecurityWebFilterChain {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange { exchange: ServerHttpSecurity.AuthorizeExchangeSpec? ->
                exchange!!
                    .pathMatchers("/health/**", "/prometheus")
                    .permitAll()
                    .pathMatchers("/api/**")
                    .hasAnyAuthority("SCOPE_eformidling:dph", "SCOPE_move/dph.read")
                    .anyExchange()
                    .authenticated()
            }
            .headers { headers: ServerHttpSecurity.HeaderSpec -> headers.frameOptions(Customizer.withDefaults()) }
            .oauth2ResourceServer { oauth2: ServerHttpSecurity.OAuth2ResourceServerSpec ->
                oauth2.authenticationManagerResolver(
                    JwtIssuerReactiveAuthenticationManagerResolver.fromTrustedIssuers(config.trustedIssuers)
                )
            }

        return http.build()
    }

    //    fun helseIdClient(helseId: HelseId, configuration: Configuration, httpClient: HttpClient)
    // =
    //        HelseIdClient(configuration, httpClient,
    // CachedHttpDiscoveryOpenIdConfiguration(helseId.issuer))
    //
    //    fun helseIdConfigurationForTest(helseId: HelseId) =
    //        Configuration(helseId.clientId, helseId.privateKey, Environment(helseId.issuer,
    // helseId.audience))
    //
    fun helseIdConfiguration(helseId: HelseId) =
        HelseIdConfiguration(Environment(helseId.issuer, helseId.audience), helseId.clientId, helseId.privateKey)
}
