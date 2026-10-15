package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

private const val APPREC_1_0_NAMESPACE = "http://www.kith.no/xmlstds/apprec/2004-11-21"
private const val APPREC_1_1_NAMESPACE = "http://www.kith.no/xmlstds/apprec/2012-02-15"

class ApplicationReceiptDeserializerTest :
    DescribeSpec({
        fun appRec(namespace: String, migVersion: String, sender: String, receiver: String, extra: String = "") =
            """
            <AppRec xmlns="$namespace">
              <MsgType V="APPREC" DN="Applikasjonskvittering"/>
              <MIGversion>$migVersion</MIGversion>
              <SoftwareName>EPJ</SoftwareName>
              <SoftwareVersion>1.0</SoftwareVersion>
              <GenDate>2026-10-14T12:00:00</GenDate>
              <Id>apprec-1</Id>
              <Sender>$sender</Sender>
              <Receiver>$receiver</Receiver>
              <Status V="2" DN="Avvist"/>
              $extra
              <OriginalMsgId>
                <MsgType V="DIALOG_NOTAT" DN="Notat"/>
                <IssueDate>2026-10-14T11:00:00</IssueDate>
                <Id>original-1</Id>
              </OriginalMsgId>
            </AppRec>
            """
                .trimIndent()

        val institution =
            """
            <Role V="1" DN="Avsender"/>
            <HCP>
              <Inst>
                <Name>Legekontor</Name>
                <Id>2000001</Id>
                <TypeId V="HER" DN="HER-id"/>
                <Dept>
                  <Type V="AVD" DN="Avdeling"/>
                  <Name>Avdeling</Name>
                  <Id>2000003</Id>
                  <TypeId V="HER" DN="HER-id"/>
                  <AdditionalId><Id>974000000</Id><Type V="ENH" DN="Organisasjonsnummer"/></AdditionalId>
                </Dept>
                <AdditionalId><Id>987654321</Id><Type V="ENH" DN="Organisasjonsnummer"/></AdditionalId>
                <HCPerson>
                  <Name>Lege Legesen</Name>
                  <Id>2000002</Id>
                  <TypeId V="HER" DN="HER-id"/>
                  <AdditionalId><Id>9144889</Id><Type V="HPR" DN="HPR-nummer"/></AdditionalId>
                </HCPerson>
              </Inst>
              <MedSpeciality V="1" S="2.16.578.1.12.4.1.1.7426" DN="Allmennmedisin"/>
              <Address>
                <Type V="RES" DN="Besøksadresse"/>
                <StreetAdr>Storgata 1</StreetAdr>
                <PostalCode>0001</PostalCode>
                <City>Oslo</City>
                <County V="0301" DN="Oslo"/>
                <Country V="NO" DN="Norge"/>
                <CityDistr V="1" DN="Sentrum"/>
                <TeleAddress V="tel:22334455"/>
              </Address>
            </HCP>
            """

        val professional =
            """
            <HCP>
              <HCProf>
                <Type V="LE" DN="Lege"/>
                <Name>Lege Legesen</Name>
                <Id>2000002</Id>
                <TypeId V="HER" DN="HER-id"/>
                <AdditionalId><Id>9144889</Id><Type V="HPR" DN="HPR-nummer"/></AdditionalId>
              </HCProf>
            </HCP>
            """

        val error = """<Error V="E10" S="2.16.578.1.12.4.1.1.8221" DN="Ugyldig fødselsnummer" OT="Feil"/>"""

        describe("AppRec v1.1") {
            it("deserializes the receipt") {
                val appRec =
                    ApplicationReceiptDeserializer.deserializeAppRec(
                        appRec(APPREC_1_1_NAMESPACE, "v1.1 2012-02-15", institution, professional, error)
                    )

                appRec.id shouldBe "apprec-1"
                appRec.status.v shouldBe "2"
                appRec.sender.hcp.inst.hcPerson.single().id shouldBe "2000002"
                appRec.receiver.hcp.hcProf.id shouldBe "2000002"
                appRec.error.single().v shouldBe "E10"
                appRec.originalMsgId.id shouldBe "original-1"
            }
        }

        describe("AppRec v1.0") {
            it("converts every field to the latest version") {
                val appRec =
                    ApplicationReceiptDeserializer.deserializeAppRec(
                        appRec(APPREC_1_0_NAMESPACE, "1.0 2004-11-21", institution, professional, error)
                    )

                appRec.msgType.v shouldBe "APPREC"
                appRec.msgType.dn shouldBe "Applikasjonskvittering"
                appRec.miGversion shouldBe "1.0 2004-11-21"
                appRec.softwareName shouldBe "EPJ"
                appRec.softwareVersion shouldBe "1.0"
                appRec.genDate.shouldNotBeNull()
                appRec.id shouldBe "apprec-1"
                appRec.status.v shouldBe "2"
                appRec.status.dn shouldBe "Avvist"

                with(appRec.error.single()) {
                    v shouldBe "E10"
                    s shouldBe "2.16.578.1.12.4.1.1.8221"
                    dn shouldBe "Ugyldig fødselsnummer"
                    ot shouldBe "Feil"
                }

                with(appRec.originalMsgId) {
                    msgType.v shouldBe "DIALOG_NOTAT"
                    issueDate.shouldNotBeNull()
                    id shouldBe "original-1"
                }

                appRec.sender.role.v shouldBe "1"
                with(appRec.sender.hcp) {
                    medSpeciality.dn shouldBe "Allmennmedisin"
                    with(inst) {
                        name shouldBe "Legekontor"
                        id shouldBe "2000001"
                        typeId.v shouldBe "HER"
                        additionalId.single().id shouldBe "987654321"
                        with(dept.single()) {
                            type.v shouldBe "AVD"
                            name shouldBe "Avdeling"
                            id shouldBe "2000003"
                            typeId.v shouldBe "HER"
                            additionalId.single().type.v shouldBe "ENH"
                        }
                        with(hcPerson.single()) {
                            name shouldBe "Lege Legesen"
                            id shouldBe "2000002"
                            typeId.v shouldBe "HER"
                            additionalId.single().id shouldBe "9144889"
                        }
                    }
                    with(address) {
                        type.v shouldBe "RES"
                        streetAdr shouldBe "Storgata 1"
                        postalCode shouldBe "0001"
                        city shouldBe "Oslo"
                        county.v shouldBe "0301"
                        country.v shouldBe "NO"
                        cityDistr.dn shouldBe "Sentrum"
                        teleAddress.single().v shouldBe "tel:22334455"
                    }
                }

                appRec.receiver.role.shouldBeNull()
                with(appRec.receiver.hcp.hcProf) {
                    type.v shouldBe "LE"
                    name shouldBe "Lege Legesen"
                    id shouldBe "2000002"
                    typeId.v shouldBe "HER"
                    additionalId.single().type.v shouldBe "HPR"
                }
            }

            it("converts a minimal receipt") {
                val appRec =
                    ApplicationReceiptDeserializer.deserializeAppRec(
                        appRec(
                            APPREC_1_0_NAMESPACE,
                            "1.0 2004-11-21",
                            "<HCP><HCProf><Id>2000002</Id></HCProf></HCP>",
                            "<HCP><Inst><Id>1000002</Id></Inst></HCP>",
                        )
                    )

                appRec.error.shouldBeEmpty()
                appRec.sender.hcp.address.shouldBeNull()
                appRec.sender.hcp.hcProf.typeId.shouldBeNull()
                appRec.receiver.hcp.inst.dept.shouldBeEmpty()
            }
        }

        describe("unsupported input") {
            it("rejects another MIGversion") {
                shouldThrow<ApplicationReceiptException> {
                        ApplicationReceiptDeserializer.deserializeAppRec(
                            appRec(APPREC_1_1_NAMESPACE, "v1.2 2020-01-01", institution, professional)
                        )
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT
            }

            it("rejects another root element") {
                shouldThrow<ApplicationReceiptException> {
                        ApplicationReceiptDeserializer.deserializeAppRec("<MsgHead/>")
                    }
                    .error shouldBe FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT
            }

            it("rejects a receipt that is not valid according to the schema") {
                shouldThrowAny {
                    ApplicationReceiptDeserializer.deserializeAppRec(
                        appRec(APPREC_1_1_NAMESPACE, "v1.1 2012-02-15", institution, professional, "<Unknown/>")
                    )
                }
            }
        }
    })
