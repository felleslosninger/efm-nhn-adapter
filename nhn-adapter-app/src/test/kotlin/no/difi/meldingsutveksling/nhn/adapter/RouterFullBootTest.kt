package no.difi.meldingsutveksling.nhn.adapter

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.verify
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@Configuration
@Primary
class TestSecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http.csrf { it.disable() }.authorizeExchange { it.anyExchange().permitAll() }.build()
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.security.user.name=testuser", "spring.security.user.password=testpass"],
)
@ActiveProfiles("unit-test")
class SecurityTest2(
    @MockkBean val flr: FastlegeregisteretClient,
    @MockkBean val arClient: AdresseregisteretClient,
    @Value("\${local.server.port}") val port: Int,
) :
    FunSpec({
        test("Load Application Context") {
            val webTestClient =
                WebTestClient.bindToServer()
                    .baseUrl("http://localhost:$port")
                    .defaultHeaders { headers -> headers.setBasicAuth("testuser", "testpass") }
                    .build()

            val PATIENT_FNR = "16822449879"
            val HERID2 = 454545
            val HERID1 = 1111
            val ORGNUM = "787878"

            every { flr.getPatientGP(PATIENT_FNR) } returns PatientGP("dummyId", HERID2)

            every { arClient.lookupHerId(HERID2) } returns
                PersonCommunicationParty(
                    HERID2,
                    "Fastlege",
                    CommunicationPartyParent(HERID1, "ParentComunicationParty", ORGNUM),
                    listOf(),
                    listOf(),
                    "Peter",
                    "",
                    "Petterson",
                )

            val result =
                webTestClient
                    .get()
                    .uri("/arlookup/16822449879")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .returnResult(ArDetails::class.java)

            result.status.is2xxSuccessful.shouldBeTrue()

            val arDetails = result.responseBody.blockFirst()
            arDetails.shouldNotBeNull()
            arDetails.herid2 shouldBeEqual HERID2
            arDetails.herid1 shouldBeEqual HERID1
            arDetails.orgNumber shouldBeEqual ORGNUM
            arDetails.derDigdirSertifikat.shouldNotBeNull()

            verify(exactly = 1) { flr.getPatientGP(any()) }
            verify(exactly = 1) { arClient.lookupHerId(any()) }
        }
    })
