package no.difi.meldingsutveksling.nhn.adapter

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringAutowireConstructorExtension
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("unit-test")
@AutoConfigureWebTestClient
class RouterBootMockEnvTest(
    @MockkBean val flr: FastlegeregisteretClient,
    @MockkBean val arClient: AdresseregisteretClient,
    @Autowired val webTestClient: WebTestClient,
) :
    FunSpec({
        test("Load Application Context") {
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
            arDetails.pemDigdirSertifikat.shouldNotBeNull()

            verify(exactly = 1) { flr.getPatientGP(any()) }
            verify(exactly = 1) { arClient.lookupHerId(any()) }
        }
    }) {

    override fun extensions(): List<Extension> = listOf(SpringAutowireConstructorExtension)
}
