package no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import jakarta.xml.ws.soap.SOAPFaultException
import java.net.ConnectException
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretApiException
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.flr.PatientGP

private const val HER_ID = 1000002
private const val FASTLEGE_HER_ID = 2000002
private const val PASIENT_FNR = "16822449879"

class AdresseregisteretServiceTest :
    DescribeSpec({
        val fastlegeregisteretClient = mockk<FastlegeregisteretClient>()
        val adresseregisteretClient = mockk<AdresseregisteretClient>()
        val service = AdresseregisteretService(fastlegeregisteretClient, adresseregisteretClient)

        val party = mockk<CommunicationParty>()
        val connectionError = ConnectException("Connection refused")
        val soapFault = mockk<SOAPFaultException>(relaxed = true)

        describe("lookupByHerId") {
            it("returns the communication party") {
                every { adresseregisteretClient.lookupHerId(HER_ID) } returns party

                service.lookupByHerId(HER_ID) shouldBeSameInstanceAs party
            }

            it("throws HerIdNotFoundException when the HER-id is unknown") {
                every { adresseregisteretClient.lookupHerId(HER_ID) } returns null

                shouldThrow<HerIdNotFoundException> { service.lookupByHerId(HER_ID) }
            }

            it("throws HerIdNotFoundException when the HER-id is invalid") {
                every { adresseregisteretClient.lookupHerId(HER_ID) } throws
                    AdresseregisteretApiException("InvalidHerIdSupplied", "Invalid HER-id", "Invalid HER-id")

                shouldThrow<HerIdNotFoundException> { service.lookupByHerId(HER_ID) }
            }

            it("rethrows other API errors") {
                val error = AdresseregisteretApiException("OtherError", "Other error", "Other error")
                every { adresseregisteretClient.lookupHerId(HER_ID) } throws error

                shouldThrow<AdresseregisteretApiException> { service.lookupByHerId(HER_ID) }.errorCode shouldBe
                    "OtherError"
            }

            it("unwraps technical errors to their cause") {
                every { adresseregisteretClient.lookupHerId(HER_ID) } throws
                    AdresseregisteretException("Technical error", connectionError)

                shouldThrow<ConnectException> { service.lookupByHerId(HER_ID) }.message shouldBe "Connection refused"
            }

            it("keeps SOAP faults wrapped") {
                val error = AdresseregisteretException("SOAP fault", soapFault)
                every { adresseregisteretClient.lookupHerId(HER_ID) } throws error

                shouldThrow<AdresseregisteretException> { service.lookupByHerId(HER_ID) }.message shouldBe "SOAP fault"
            }

            it("keeps errors without a cause as is") {
                val error = AdresseregisteretException("No cause", null)
                every { adresseregisteretClient.lookupHerId(HER_ID) } throws error

                shouldThrow<AdresseregisteretException> { service.lookupByHerId(HER_ID) }.message shouldBe "No cause"
            }
        }

        describe("lookupByNhnIdentifier") {
            it("looks up a HER-id directly") {
                every { adresseregisteretClient.lookupHerId(HER_ID) } returns party

                service.lookupByNhnIdentifier(NhnIdentifier.herId(HER_ID)) shouldBeSameInstanceAs party
            }

            it("looks up the fastlege of a patient") {
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } returns
                    PatientGP(PASIENT_FNR, FASTLEGE_HER_ID)
                every { adresseregisteretClient.lookupHerId(FASTLEGE_HER_ID) } returns party

                service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR")) shouldBeSameInstanceAs
                    party
            }

            it("throws HerIdNotFoundException when the patient is not found in Fastlegeregisteret") {
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } returns null

                shouldThrow<HerIdNotFoundException> {
                    service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR"))
                }
            }

            it("throws HerIdNotFoundException when the patient has no fastlege") {
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } returns PatientGP(PASIENT_FNR, null)

                shouldThrow<HerIdNotFoundException> {
                    service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR"))
                }
            }

            it("throws HerIdNotFoundException when the patient has no fastlege contract") {
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } throws
                    FastlegeregisteretApiException(
                        "Fault",
                        "ArgumentException: Personen er ikke tilknyttet fastlegekontrakt",
                        "No contract",
                    )

                shouldThrow<HerIdNotFoundException> {
                    service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR"))
                }
            }

            it("rethrows other Fastlegeregisteret API errors") {
                val error = FastlegeregisteretApiException("Fault", "Other fault", "Other fault")
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } throws error

                shouldThrow<FastlegeregisteretApiException> {
                        service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR"))
                    }
                    .faultMessage shouldBe "Other fault"
            }

            it("unwraps technical Fastlegeregisteret errors to their cause") {
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } throws
                    FastlegeregisteretException("Technical error", connectionError)

                shouldThrow<ConnectException> {
                        service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR"))
                    }
                    .message shouldBe "Connection refused"
            }

            it("keeps Fastlegeregisteret SOAP faults wrapped") {
                val error = FastlegeregisteretException("SOAP fault", soapFault)
                every { fastlegeregisteretClient.getPatientGP(PASIENT_FNR) } throws error

                shouldThrow<FastlegeregisteretException> {
                        service.lookupByNhnIdentifier(NhnIdentifier.parse("fastlege-for:$PASIENT_FNR"))
                    }
                    .message shouldBe "SOAP fault"
            }
        }
    })
