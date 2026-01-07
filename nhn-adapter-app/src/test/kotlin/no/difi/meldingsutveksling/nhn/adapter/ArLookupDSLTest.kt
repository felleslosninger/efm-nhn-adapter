package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.floats.exactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.util.encodeBase64
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.xml.bind.JAXBElement
import jakarta.xml.soap.SOAPFactory
import jakarta.xml.ws.soap.SOAPFaultException
import javax.xml.namespace.QName
import kotlinx.coroutines.reactive.awaitFirst
import no.difi.meldingsutveksling.nhn.adapter.beans.IntegrationBeans.arClient
import no.difi.meldingsutveksling.nhn.adapter.config.CryptoConfig
import no.difi.meldingsutveksling.nhn.adapter.crypto.KeystoreManager
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretService
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretService
import no.ks.fiks.nhn.flr.PatientGP
import no.nhn.common.flr.GenericFault
import no.nhn.schemas.reg.flr.IFlrReadOperationsGetPatientGPDetailsGenericFaultFaultFaultMessage
import no.nhn.schemas.reg.flr.PatientToGPContractAssociation
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ArLookupDSLTest() :
    ShouldSpec({
        context("Test AR lookup") {
            val arLookupContext = BeanRegistrarDsl {
                registerBean<FastlegeregisteretClient> { mockk() }
                registerBean<DecoratingFlrClient>() { DecoratingFlrClient(bean(), listOf()) }
                registerBean<AdresseregisteretClient> { mockk() }
                registerBean<KeystoreManager>() { KeystoreManager(testCryptoConfig) }
                testCoRouter { ctx -> arLookup(ctx.bean(), ctx.bean(), ctx.bean()) }
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
                arDetails.shouldNotBeNull()
                arDetails.herid2 shouldBeEqual HERID2
                arDetails.herid1 shouldBeEqual HERID1
                arDetails.orgNumber shouldBeEqual ORGNUM
                arDetails.derDigdirSertifikat.shouldNotBeNull()

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
                arDetails.shouldNotBeNull()
                arDetails.herid2 shouldBeEqual HERID2
                arDetails.herid1 shouldBeEqual HERID1
                arDetails.orgNumber shouldBeEqual ORGNUM
                arDetails.derDigdirSertifikat.shouldNotBeNull()
            }

            should("Respond with 404 when identifier is not found in Fastlegeregisteret") {
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

        context("Covers msh-client fail scenarios on a semi trasport level") {
            val arLookupContext = BeanRegistrarDsl {
                registerBean<FastlegeregisteretService> { mockk<FastlegeregisteretService>() }
                registerBean<FastlegeregisteretClient> { FastlegeregisteretClient(bean()) }
                registerBean<DecoratingFlrClient>() { DecoratingFlrClient(bean(), listOf()) }
                registerBean<AdresseregisteretService> { mockk() }
                registerBean<AdresseregisteretClient> { AdresseregisteretClient(bean()) }
                registerBean<KeystoreManager>() { KeystoreManager(testCryptoConfig) }
                testCoRouter { ctx -> arLookup(ctx.bean(), ctx.bean(), ctx.bean()) }
            }
            val context =
                AnnotationConfigApplicationContext().apply {
                    register(arLookupContext)
                    refresh()
                }

            val webTestClient = webTestClient(context.getBean())

            afterTest() {
                clearMocks(context.getBean<FastlegeregisteretService>(), context.getBean<AdresseregisteretService>())
            }

            should("Respond with 404 when CommunicationException is thrown") {
                val flr = context.getBean<FastlegeregisteretService>()
                // val arClient = context.getBean<AdresseregisteretClient>()

                val PATIENT_FNR = "16822449879"

                every { flr.getPatientGPDetails(PATIENT_FNR) } throws
                    IFlrReadOperationsGetPatientGPDetailsGenericFaultFaultFaultMessage(
                            "Test generic fault",
                            GenericFault().apply {
                                this.message =
                                    fakeJaxBElement("ArgumentException: Personen er ikke tilknyttet fastlegekontrakt")
                                this.errorCode = fakeJaxBElement("E404")
                            },
                        )
                        .apply {}

                webTestClient.get().uri("/arlookup/$PATIENT_FNR").exchange().returnResult(ApiError::class.java).also {
                    it.status shouldBe HttpStatus.NOT_FOUND
                    it.responseBody.awaitFirst().message shouldBe "HerId is not found"
                }
            }

            should("Respond with 500 when fastlegeregister client throws generic exception") {
                val flr = context.getBean<FastlegeregisteretService>()
                // val arClient = context.getBean<AdresseregisteretClient>()

                val PATIENT_FNR = "16822449879"

                every { flr.getPatientGPDetails(PATIENT_FNR) } throws Exception("Test exception")

                webTestClient.get().uri("/arlookup/$PATIENT_FNR").exchange().returnResult(ApiError::class.java).also {
                    it.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                    it.responseBody.awaitFirst().message.shouldNotBeNull() shouldBeEqual
                        "Not able to process, try later. ErrorCode: E7777"
                }
            }

            should("Respond with 500 when adressregister client throws generic exception") {
                val arService = context.getBean<AdresseregisteretService>()

                val HER_ID = 1212

                every { arService.getCommunicationPartyDetails(HER_ID) } throws Exception("Test exception")

                webTestClient.get().uri("/arlookup/$HER_ID").exchange().returnResult(ApiError::class.java).also {
                    it.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                    it.responseBody.awaitFirst().message.shouldNotBeNull() shouldBeEqual
                        "Not able to process, try later. ErrorCode: E7777"
                }
            }

            should("Respond with 500 when on Fastlegeregister SoapFault") {
                val flr = context.getBean<FastlegeregisteretService>()

                val PATIENT_FNR = "16822449879"

                every { flr.getPatientGPDetails(PATIENT_FNR) } throws
                    SOAPFaultException(
                        SOAPFactory.newInstance()
                            .createFault(
                                "Test error",
                                QName(
                                    "http://schemas.xmlsoap.org/soap/envelope/",
                                    "Client", // local part
                                    "env", // prefix (optional but common)
                                ),
                            )
                    )

                webTestClient
                    .get()
                    .uri("/arlookup/$PATIENT_FNR")
                    .exchange()
                    .returnResult(ApiError::class.java)
                    .also {
                        it.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                        it.responseBody.awaitFirst().message.shouldNotBeNull() shouldBeEqual
                            "Not able to process, try later. ErrorCode: E7779"
                    }
                    .status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }

            should("Respond with 500 when on AdressRegister SoapFault") {
                val flr = context.getBean<FastlegeregisteretService>()
                val arService = context.getBean<AdresseregisteretService>()

                val PATIENT_FNR = "16822449879"

                val patientToGp: PatientToGPContractAssociation = mockk()
                val qName = QName("http://test.com/test", "myInteger")
                val herId = JAXBElement(qName, Int::class.java, 1234)
                val patientNin = JAXBElement(qName, String::class.java, "77777")

                every { patientToGp.gpHerId } returns herId
                every { patientToGp.patientNIN } returns patientNin

                every { flr.getPatientGPDetails(PATIENT_FNR) } returns patientToGp

                every { arService.getCommunicationPartyDetails(1234) } throws
                    SOAPFaultException(
                        SOAPFactory.newInstance()
                            .createFault(
                                "Test error",
                                QName(
                                    "http://schemas.xmlsoap.org/soap/envelope/",
                                    "Client", // local part
                                    "env", // prefix (optional but common)
                                ),
                            )
                    )

                webTestClient
                    .get()
                    .uri("/arlookup/$PATIENT_FNR")
                    .exchange()
                    .returnResult(ApiError::class.java)
                    .also {
                        it.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                        it.responseBody.awaitFirst().message.shouldNotBeNull() shouldBeEqual
                            "Not able to process, try later. ErrorCode: E7778"
                    }
                    .status shouldBe HttpStatus.INTERNAL_SERVER_ERROR

                verify(exactly = 1) { arService.getCommunicationPartyDetails(1234) }
            }
        }
    })

inline fun <reified T> fakeJaxBElement(value: T): JAXBElement<T> {
    val qName = QName("http://test.com/test", "fakeJaxBElement")
    return JAXBElement<T>(qName, T::class.java, value)
}

val testCryptoConfig =
    CryptoConfig(
        "unit-test",
        Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("unit-test-sertifikat.p12")
            .readAllBytes()
            .encodeBase64(),
        password = "test",
        file = null,
        type = "PKCS12",
    )
