package no.difi.meldingsutveksling.nhn.adapter.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.model.Claims
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono

class ClientContextTest :
    DescribeSpec({
        describe("ClientContextImpl") {
            it("should map properties from JWT claims") {
                val jwt = mockk<Jwt>()
                every { jwt.getClaimAsString(Claims.CLIENT_ID) } returns "test-client"
                every { jwt.getClaimAsMap(Claims.CONSUMER) } returns mapOf("ID" to "0192:123456789")
                every { jwt.getClaimAsMap(Claims.SUPPLIER) } returns mapOf("ID" to "0192:987654321")
                every { jwt.getClaimAsString(Claims.DELEGATION_SOURCE) } returns "altinn"
                every { jwt.getClaimAsString(Claims.SCOPE) } returns "scope1 scope2"

                val clientContext = ClientContextImpl(jwt)

                clientContext.clientId shouldBe "test-client"
                clientContext.consumer.organizationIdentifier shouldBe "123456789"
                clientContext.supplier?.organizationIdentifier shouldBe "987654321"
                clientContext.onBehalfOfOrgNumber shouldBe "123456789"
                clientContext.orgNumber shouldBe "987654321"
                clientContext.delegationSource shouldBe "altinn"
                clientContext.scopes shouldBe setOf("scope1", "scope2")
            }

            it("should fall back to consumer org number if supplier is missing") {
                val jwt = mockk<Jwt>()
                every { jwt.getClaimAsString(Claims.CLIENT_ID) } returns "test-client"
                every { jwt.getClaimAsMap(Claims.CONSUMER) } returns mapOf("ID" to "0192:123456789")
                every { jwt.getClaimAsMap(Claims.SUPPLIER) } returns null
                every { jwt.getClaimAsString(Claims.DELEGATION_SOURCE) } returns null
                every { jwt.getClaimAsString(Claims.SCOPE) } returns "scope1"

                val clientContext = ClientContextImpl(jwt)

                clientContext.orgNumber shouldBe "123456789"
            }
        }

        describe("Jwt extension functions") {
            val jwt = mockk<Jwt>()

            it("getClientId should return clientId claim") {
                every { jwt.getClaimAsString(Claims.CLIENT_ID) } returns "client-abc"
                jwt.getClientId() shouldBe "client-abc"
            }

            it("getConsumer should return Iso6523 from consumer claim") {
                every { jwt.getClaimAsMap(Claims.CONSUMER) } returns mapOf("ID" to "0192:123")
                jwt.getConsumer() shouldBe Iso6523.parse("0192:123")
            }

            it("getSupplier should return Iso6523 from supplier claim or null") {
                every { jwt.getClaimAsMap(Claims.SUPPLIER) } returns mapOf("ID" to "0192:456")
                jwt.getSupplier() shouldBe Iso6523.parse("0192:456")

                every { jwt.getClaimAsMap(Claims.SUPPLIER) } returns null
                jwt.getSupplier() shouldBe null
            }

            it("getDelegationSource should return delegation_source claim") {
                every { jwt.getClaimAsString(Claims.DELEGATION_SOURCE) } returns "src-1"
                jwt.getDelegationSource() shouldBe "src-1"
            }

            it("getScopes should return set of scopes") {
                every { jwt.getClaimAsString(Claims.SCOPE) } returns "a b c"
                jwt.getScopes() shouldBe setOf("a", "b", "c")
            }

            it("getScopes should return an empty set when the scope claim is missing") {
                every { jwt.getClaimAsString(Claims.SCOPE) } returns null
                jwt.getScopes() shouldBe emptySet()
            }

            it("getClientId should fail when the claim is missing") {
                every { jwt.getClaimAsString(Claims.CLIENT_ID) } returns null
                shouldThrow<IllegalStateException> { jwt.getClientId() }.message shouldBe
                    "Missing claim ${Claims.CLIENT_ID}"
            }

            it("getConsumer should fail when the claim is missing") {
                every { jwt.getClaimAsMap(Claims.CONSUMER) } returns null
                shouldThrow<IllegalStateException> { jwt.getConsumer() }.message shouldBe
                    "Missing claim ${Claims.CONSUMER}"
            }

            it("getConsumer should fail when the claim has no ID") {
                every { jwt.getClaimAsMap(Claims.CONSUMER) } returns mapOf("authority" to "iso6523-actorid-upis")
                shouldThrow<IllegalStateException> { jwt.getConsumer() }
            }
        }

        describe("getClientContext") {
            it("should create a client context from the JWT in the reactive security context") {
                val jwt =
                    Jwt.withTokenValue("token").header("alg", "none").claim(Claims.CLIENT_ID, "test-client").build()
                val securityContext = SecurityContextImpl(JwtAuthenticationToken(jwt))

                val clientContext =
                    mono { getClientContext() }
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
                        .awaitSingle()

                clientContext.clientId shouldBe "test-client"
            }

            it("should fail when the security context is not JWT authenticated") {
                val securityContext = SecurityContextImpl(mockk<Authentication>())

                shouldThrow<IllegalStateException> {
                        mono { getClientContext() }
                            .contextWrite(
                                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext))
                            )
                            .awaitSingle()
                    }
                    .message shouldBe "Expected a JWT authenticated security context"
            }
        }

        describe("SecurityContext extension functions") {
            it("getJwt should return Jwt if authentication is JwtAuthenticationToken") {
                val jwt = mockk<Jwt>()
                val auth = mockk<JwtAuthenticationToken>()
                every { auth.token } returns jwt
                val securityContext = mockk<SecurityContext>()
                every { securityContext.authentication } returns auth

                securityContext.getJwt() shouldBe jwt
            }

            it("getJwt should return null if authentication is not JwtAuthenticationToken") {
                val auth = mockk<Authentication>()
                val securityContext = mockk<SecurityContext>()
                every { securityContext.authentication } returns auth

                securityContext.getJwt() shouldBe null
            }
        }
    })
