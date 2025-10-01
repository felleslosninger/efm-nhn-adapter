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
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.model.FeilmeldingForApplikasjonskvitteringSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.IdSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.StatusForMottakAvMeldingSerializer
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import no.ks.fiks.nhn.msh.Id
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("unit-test")
class SecurityTest(
    @MockkBean val flr: FastlegeregisteretClient,
    @MockkBean val arClient: AdresseregisteretClient,
    @Autowired val context: ApplicationContext,
) :
    FunSpec({
        test("Load Application Context") {
            val webTestClient =
                WebTestClient.bindToApplicationContext(context)
                    .configureClient()
                    .codecs { cfg ->
                        val json = Json {
                            ignoreUnknownKeys = true
                            classDiscriminator = "type"
                            serializersModule = SerializersModule {
                                contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
                                contextual(
                                    FeilmeldingForApplikasjonskvittering::class,
                                    FeilmeldingForApplikasjonskvitteringSerializer,
                                )
                                contextual(Id::class, IdSerializer)
                            }
                        }
                        cfg.defaultCodecs().configureDefaultCodec {}

                        cfg.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonDecoder(json))
                        cfg.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonEncoder(json))
                    }
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
