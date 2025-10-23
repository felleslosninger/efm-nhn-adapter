package no.difi.meldingsutveksling.nhn.adapter.beans

import no.difi.meldingsutveksling.nhn.adapter.config.HelseId
import no.ks.fiks.helseid.CachedHttpDiscoveryOpenIdConfiguration
import no.ks.fiks.helseid.Configuration
import no.ks.fiks.helseid.Environment
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import org.apache.hc.client5.http.classic.HttpClient
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver
import org.springframework.security.web.server.SecurityWebFilterChain

object SecurityBeans {
    fun userDetailsService(passwordEncoder: PasswordEncoder): MapReactiveUserDetailsService =
        User.builder()
            .passwordEncoder { passwordEncoder.encode(it) }
            .username("testUser")
            .password("testPassword")
            .roles()
            .build()
            .let { MapReactiveUserDetailsService(it) }

    fun userDetailsRepositoryReactiveAuthenticationManager(
        passwordEncoder: PasswordEncoder,
        mapReactiveUserDetailsService: MapReactiveUserDetailsService,
    ) =
        UserDetailsRepositoryReactiveAuthenticationManager(mapReactiveUserDetailsService).apply {
            setPasswordEncoder(passwordEncoder)
        }

    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val jwtIssuerReactiveAuthenticationManagerResolver: JwtIssuerReactiveAuthenticationManagerResolver? =
            JwtIssuerReactiveAuthenticationManagerResolver.fromTrustedIssuers("https://test.maskinporten.no/")

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(
                Customizer { exchange: ServerHttpSecurity.AuthorizeExchangeSpec? ->
                    exchange!!
                        .pathMatchers("/nhn-adapter/**", "/health/**", "/prometheus", "/h2-console/**", "/jwk")
                        .permitAll()
                        .pathMatchers("/api/**")
                        .authenticated()
                        .anyExchange()
                        .authenticated()
                }
            )
            .headers { headers: ServerHttpSecurity.HeaderSpec -> headers.frameOptions(Customizer.withDefaults()) }
            .httpBasic(Customizer.withDefaults())
            .oauth2ResourceServer { oauth2: ServerHttpSecurity.OAuth2ResourceServerSpec ->
                oauth2.authenticationManagerResolver(jwtIssuerReactiveAuthenticationManagerResolver)
            }

        return http.build()
    }

    fun helseIdClient(helseId: HelseId, configuration: Configuration, httpClient: HttpClient) =
        HelseIdClient(configuration, httpClient, CachedHttpDiscoveryOpenIdConfiguration(helseId.issuer))

    fun helseIdConfigurationForTest(helseId: HelseId) =
        Configuration(helseId.clientId, helseId.privateKey, Environment(helseId.issuer, helseId.audience))

    fun helseIdConfiguration(helseId: HelseId) =
        HelseIdConfiguration(Environment(helseId.issuer, helseId.audience), helseId.clientId, helseId.privateKey)
}
