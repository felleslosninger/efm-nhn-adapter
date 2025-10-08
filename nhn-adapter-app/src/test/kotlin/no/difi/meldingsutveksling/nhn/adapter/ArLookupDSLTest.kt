package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ArLookupDSLTest() :
    ShouldSpec({
        context("Test AR lookup") {
            val arLookupContext = BeanRegistrarDsl {
                registerBean<FastlegeregisteretClient>(::mockk)
                registerBean<DecoratingFlrClient>() { DecoratingFlrClient(bean(), listOf()) }
                registerBean<AdresseregisteretClient> { mockk() }
                testCoRouter { ctx -> arLookup(ctx.bean(), ctx.bean()) }
            }
            val context =
                AnnotationConfigApplicationContext().apply {
                    register(arLookupContext)
                    refresh()
                }

            val webTestClient = webTestClient(context.getBean())

            afterTest() {
                clearMocks(context.getBean<FastlegeregisteretClient>(), context.getBean<AdresseregisteretClient>())
            }

            should("Return AR information when valid FNR is provided") {
                val flr = context.getBean<FastlegeregisteretClient>()
                val arClient = context.getBean<AdresseregisteretClient>()
                val PATIENT_FNR = "16822449879"
                val HERID2 = 454545
                val HERID1 = 1111
                val ORGNUM = "787878"

                every { flr.getPatientGP(PATIENT_FNR) } returns PatientGP("dummyId", HERID2)

                every { arClient.lookupHerId(HERID2) } returns testFastlegeCommunicationParty(HERID2, HERID1, ORGNUM)

                val result =
                    webTestClient
                        .get()
                        .uri("/arlookup/$PATIENT_FNR")
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

            should("Should return AR information when valid HerId is provided") {
                val flr = context.getBean<FastlegeregisteretClient>()
                val arClient = context.getBean<AdresseregisteretClient>()

                val HERID2 = 878787
                val HERID1 = 2222
                val ORGNUM = "787878"

                every { arClient.lookupHerId(HERID2) } returns testNhnServiceCommunicationParty(HERID2, HERID1, ORGNUM)

                val result =
                    webTestClient
                        .get()
                        .uri("/arlookup/$HERID2")
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .returnResult(ArDetails::class.java)

                result.status.is2xxSuccessful.shouldBeTrue()

                verify(exactly = 0) { flr.getPatientGP(any()) }

                verify(exactly = 1) { arClient.lookupHerId(HERID2) }

                val arDetails = result.responseBody.blockFirst()
                arDetails.herid2 shouldBeEqual HERID2
                arDetails.herid1 shouldBeEqual HERID1
                arDetails.orgNumber shouldBeEqual ORGNUM
                arDetails.pemDigdirSertifikat.shouldNotBeNull()
            }

            should("Respond with 404 when identifier is not found") {
                val flr = context.getBean<FastlegeregisteretClient>()
                val arClient = context.getBean<AdresseregisteretClient>()

                val PATIENT_FNR_SOM_FINNES_IKKE = "29039900147"
                val HERID_SOM_FINNES_IKKE = 12312323

                every { flr.getPatientGP(any()) } throws
                    FastlegeregisteretApiException(
                        "Feil",
                        "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt",
                        "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt",
                    )
                every { arClient.lookupHerId(HERID_SOM_FINNES_IKKE) } throws
                    AdresseregisteretApiException("InvalidHerIdSupplied", "HerID not found", "HerID not found")

                webTestClient
                    .get()
                    .uri("/arlookup/$PATIENT_FNR_SOM_FINNES_IKKE")
                    .exchange()
                    .returnResult(ArDetails::class.java)
                    .status shouldBe HttpStatus.NOT_FOUND

                webTestClient
                    .get()
                    .uri("/arlookup/$HERID_SOM_FINNES_IKKE")
                    .exchange()
                    .returnResult(ArDetails::class.java)
                    .status shouldBe HttpStatus.NOT_FOUND
            }

            withData(
                mapOf(
                    "Negative value test" to "-1000",
                    "big number" to "12313123123123123",
                    "alphanumeric instead of digit" to "sdfdsf233",
                )
            ) { invalidIdentifier ->
                val result =
                    webTestClient
                        .get()
                        .uri("/arlookup/$invalidIdentifier")
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .returnResult(ArDetails::class.java)

                result.status.is4xxClientError.shouldBeTrue()
            }
        }
    })
