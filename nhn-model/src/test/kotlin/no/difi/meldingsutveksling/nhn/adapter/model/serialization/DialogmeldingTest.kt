package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import javax.xml.datatype.DatatypeFactory
import no.kith.xmlstds.CS
import no.kith.xmlstds.CV
import no.kith.xmlstds.dialog._2006_10_11.Dialogmelding as Dialogmelding1_0
import no.kith.xmlstds.dialog._2006_10_11.Foresporsel as Foresporsel1_0
import no.kith.xmlstds.dialog._2006_10_11.HealthcareProfessional as HealthcareProfessional1_0
import no.kith.xmlstds.dialog._2006_10_11.Notat as Notat1_0
import no.kith.xmlstds.dialog._2006_10_11.Person as Person1_0
import no.kith.xmlstds.dialog._2006_10_11.RollerRelatertNotat as RollerRelatertNotat1_0
import no.kith.xmlstds.felleskomponent1.Address
import no.kith.xmlstds.felleskomponent1.Ident
import no.kith.xmlstds.felleskomponent1.TeleCom
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

private val datatypeFactory = DatatypeFactory.newInstance()

private fun cv(value: String) =
    CV().apply {
        v = value
        dn = "Display $value"
    }

private fun cs(value: String) =
    CS().apply {
        v = value
        dn = "Display $value"
    }

private fun date(value: String) = datatypeFactory.newXMLGregorianCalendar(value)

class DialogmeldingTest :
    DescribeSpec({
        describe("DialogmeldingDeserializer") {
            it("deserializes a valid Dialogmelding") {
                val dialogmelding =
                    DialogmeldingDeserializer.deserializeDialogmelding(
                        """
                        <Dialogmelding xmlns="http://www.kith.no/xmlstds/dialog/2013-01-23">
                          <Notat>
                            <TemaKodet V="6" S="2.16.578.1.12.4.1.1.7322" DN="Henvendelse om pasient"/>
                            <Tema>Test tema</Tema>
                            <TekstNotatInnhold xsi:type="xs:string" xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">Testing 1 2 3</TekstNotatInnhold>
                          </Notat>
                        </Dialogmelding>
                        """
                            .trimIndent()
                    )

                with(dialogmelding.notat.single()) {
                    tema shouldBe "Test tema"
                    temaKodet.v shouldBe "6"
                    tekstNotatInnhold.toString() shouldContain "Testing 1 2 3"
                }
            }

            it("rejects a Dialogmelding that is not valid according to the schema") {
                // TemaKodet is required in a Notat
                shouldThrow<ApplicationReceiptException> {
                        DialogmeldingDeserializer.deserializeDialogmelding(
                            """
                            <Dialogmelding xmlns="http://www.kith.no/xmlstds/dialog/2013-01-23">
                              <Notat><Tema>Test tema</Tema></Notat>
                            </Dialogmelding>
                            """
                        )
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
            }

            it("rejects malformed XML") {
                shouldThrow<ApplicationReceiptException> { DialogmeldingDeserializer.deserializeDialogmelding("<Dialog") }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
            }
        }

        describe("DialogmeldingConverter") {
            it("converts a Dialogmelding 1.0 with notat, forespørsel and related roles") {
                val address = Address()
                val ident = Ident()
                val teleCom = TeleCom()
                val healthcareProfessional =
                    HealthcareProfessional1_0().apply {
                        typeHealthcareProfessional = cs("LE")
                        roleToPatient = cv("6")
                        familyName = "Legesen"
                        middleName = "Mellom"
                        givenName = "Lege"
                        dateOfBirth = date("1970-01-01")
                        sex = cs("1")
                        nationality = cs("NO")
                        this.address = address
                        this.ident = listOf(ident)
                        this.teleCom = listOf(teleCom)
                    }
                val person =
                    Person1_0().apply {
                        familyName = "Nordmann"
                        middleName = null
                        givenName = "Kari"
                        dateOfBirth = date("1980-02-02")
                        sex = cs("2")
                        nationality = cs("NO")
                        this.address = address
                        this.ident = listOf(ident)
                        this.teleCom = listOf(teleCom)
                    }
                val roller =
                    RollerRelatertNotat1_0().apply {
                        rolleNotat = cv("1")
                        roleToPatient = cv("2")
                        this.healthcareProfessional = healthcareProfessional
                        this.person = person
                    }
                val foresporsel =
                    Foresporsel1_0().apply {
                        typeForesp = cv("F1")
                        sporsmal = "Spørsmål"
                        formål = cv("FO")
                        begrunnelse = "Begrunnelse"
                        hastegrad = cs("H")
                        fraDato = date("2026-01-01")
                        tilDato = date("2026-12-31")
                        typeJournalinfo = cv("J1")
                        dokIdForesp = "foresporsel-1"
                        rollerRelatertNotat = listOf(roller)
                    }
                val input =
                    Dialogmelding1_0().apply {
                        sakstypeKodet = cv("S1")
                        sakstype = "Sakstype"
                        this.foresporsel = listOf(foresporsel)
                        notat =
                            listOf(
                                Notat1_0().apply {
                                    temaKodet = cv("6")
                                    tema = "Tema"
                                    tekstNotatInnhold = "Innhold"
                                    merknad = "Merknad"
                                    dokIdNotat = "notat-1"
                                    datoNotat = date("2026-10-14")
                                    this.foresporsel = foresporsel
                                    rollerRelatertNotat = listOf(roller)
                                }
                            )
                    }

                val dialogmelding = DialogmeldingConverter.toLatest(input)

                dialogmelding.sakstypeKodet.v shouldBe "S1"
                dialogmelding.sakstype shouldBe "Sakstype"

                with(dialogmelding.foresporsel.single()) {
                    typeForesp.v shouldBe "F1"
                    // The coded information is carried over from the type of forespørsel
                    kodetOpplysning.v shouldBe "F1"
                    sporsmal shouldBe "Spørsmål"
                    formål.v shouldBe "FO"
                    begrunnelse shouldBe "Begrunnelse"
                    hastegrad.v shouldBe "H"
                    fraDato shouldBe date("2026-01-01")
                    tilDato shouldBe date("2026-12-31")
                    typeJournalinfo.single().v shouldBe "J1"
                    dokIdForesp shouldBe "foresporsel-1"
                }

                with(dialogmelding.notat.single()) {
                    temaKodet.v shouldBe "6"
                    tema shouldBe "Tema"
                    tekstNotatInnhold shouldBe "Innhold"
                    merknad shouldBe "Merknad"
                    dokIdNotat shouldBe "notat-1"
                    datoNotat shouldBe date("2026-10-14")
                    foresporsel.dokIdForesp shouldBe "foresporsel-1"

                    with(rollerRelatertNotat.single()) {
                        rolleNotat.v shouldBe "1"
                        roleToPatient.v shouldBe "2"
                        with(healthcareProfessional) {
                            typeHealthcareProfessional.v shouldBe "LE"
                            roleToPatient.v shouldBe "6"
                            familyName shouldBe "Legesen"
                            middleName shouldBe "Mellom"
                            givenName shouldBe "Lege"
                            dateOfBirth shouldBe date("1970-01-01")
                            sex.v shouldBe "1"
                            nationality.v shouldBe "NO"
                            this.address shouldBeSameInstanceAs address
                            this.ident.single() shouldBeSameInstanceAs ident
                            this.teleCom.single() shouldBeSameInstanceAs teleCom
                        }
                        with(person) {
                            familyName shouldBe "Nordmann"
                            middleName.shouldBeNull()
                            givenName shouldBe "Kari"
                            dateOfBirth shouldBe date("1980-02-02")
                            sex.v shouldBe "2"
                            nationality.v shouldBe "NO"
                            this.address shouldBeSameInstanceAs address
                            this.ident.single() shouldBeSameInstanceAs ident
                            this.teleCom.single() shouldBeSameInstanceAs teleCom
                        }
                    }
                }
            }

            it("converts an empty Dialogmelding 1.0") {
                val dialogmelding = DialogmeldingConverter.toLatest(Dialogmelding1_0())

                dialogmelding.sakstypeKodet.shouldBeNull()
                dialogmelding.foresporsel.shouldBeEmpty()
                dialogmelding.notat.shouldBeEmpty()
            }

            it("converts related roles without person details") {
                val dialogmelding =
                    DialogmeldingConverter.toLatest(
                        Dialogmelding1_0().apply {
                            notat = listOf(Notat1_0().apply { rollerRelatertNotat = listOf(RollerRelatertNotat1_0()) })
                        }
                    )

                with(dialogmelding.notat.single()) {
                    foresporsel.shouldBeNull()
                    with(rollerRelatertNotat.single()) {
                        healthcareProfessional.shouldBeNull()
                        person.shouldBeNull()
                    }
                }
            }
        }
    })
