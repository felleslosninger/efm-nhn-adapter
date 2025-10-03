package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.MediaType

class RouterDSLTest() :
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

            val webTestClient = webTestClient(context.getBean()) { this.defaultCookie("test", "test") }

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

                every { arClient.lookupHerId(HERID2) } returns mockFastlegeCommunicationParty(HERID2, HERID1, ORGNUM)

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

            should("Should return AR information when valid HerId is provided") {
                val flr = context.getBean<FastlegeregisteretClient>()
                val arClient = context.getBean<AdresseregisteretClient>()

                val HERID2 = 878787
                val HERID1 = 2222
                val ORGNUM = "787878"

                every { arClient.lookupHerId(HERID2) } returns mockNhnServiceCommunicationParty(HERID2, HERID1, ORGNUM)

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

            withData(
                mapOf(
                    "Negative value test" to "-1000",
                    "big number" to "12313123123123123",
                    "alphanumeric instead of digit" to "sdfdsf233",
                )
            ) { invalidIdentifier ->
                val HERID1 = 2222
                val ORGNUM = "787878"

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
