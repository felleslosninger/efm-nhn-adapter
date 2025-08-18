package no.difi.meldingsutveksling.nhn.adapter

import org.springframework.security.config.Customizer
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver
import org.springframework.security.web.server.SecurityWebFilterChain

fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
    val jwtIssuerReactiveAuthenticationManagerResolver: JwtIssuerReactiveAuthenticationManagerResolver? =
        JwtIssuerReactiveAuthenticationManagerResolver.fromTrustedIssuers("https://test.maskinporten.no/")

    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            Customizer { exchange: ServerHttpSecurity.AuthorizeExchangeSpec? ->
                exchange!!
                    .pathMatchers("/health/**", "/prometheus", "/h2-console/**", "/jwk")
                    .permitAll()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .authenticated()
            }
        )
        .headers { headers: ServerHttpSecurity.HeaderSpec -> headers.frameOptions(withDefaults()) }
        .httpBasic(withDefaults())
        .oauth2ResourceServer { oauth2: ServerHttpSecurity.OAuth2ResourceServerSpec ->
            oauth2.authenticationManagerResolver(jwtIssuerReactiveAuthenticationManagerResolver)
        }

    return http.build()
}
