package no.difi.meldingsutveksling.nhn.adapter

import org.springframework.security.config.Customizer
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.*
import org.springframework.security.config.web.server.ServerHttpSecurity.HeaderSpec.FrameOptionsSpec
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver
import org.springframework.security.web.server.SecurityWebFilterChain


    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val jwtIssuerReactiveAuthenticationManagerResolver: JwtIssuerReactiveAuthenticationManagerResolver /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? =
            JwtIssuerReactiveAuthenticationManagerResolver.fromTrustedIssuers("https://test.maskinporten.no/")

        http
            .csrf( CsrfSpec::disable )
            .authorizeExchange(Customizer { exchange: AuthorizeExchangeSpec? ->
                exchange!!
                    .pathMatchers("/health/**", "/prometheus", "/h2-console/**", "/jwk").permitAll()
                    .pathMatchers("/api/**").authenticated()
                    .anyExchange().authenticated()
            }
            )
            .headers(Customizer {
                headers: HeaderSpec -> headers.frameOptions(withDefaults<FrameOptionsSpec?>()) })
            .httpBasic(withDefaults<HttpBasicSpec?>())
            .oauth2ResourceServer(Customizer { oauth2: OAuth2ResourceServerSpec ->
                oauth2
                    .authenticationManagerResolver(
                        jwtIssuerReactiveAuthenticationManagerResolver
                    )
            })

        return http.build()
    }