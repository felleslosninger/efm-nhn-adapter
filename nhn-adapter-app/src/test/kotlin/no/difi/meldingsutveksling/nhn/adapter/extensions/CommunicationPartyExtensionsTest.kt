package no.difi.meldingsutveksling.nhn.adapter.extensions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.difi.meldingsutveksling.nhn.adapter.ArTestData
import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.ar.rest.model.PostalAddress
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty as MshOrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.PersonCommunicationParty as MshPersonCommunicationParty
import no.ks.fiks.nhn.msh.PersonId

private const val PARENT_HER_ID = 2000001
private const val PARENT_ORG_NUMBER = "987654321"
private const val CHILD_HER_ID = 2000002
private const val CHILD_ORG_NUMBER = "123456789"

class CommunicationPartyExtensionsTest :
    DescribeSpec({
        val parent = ArTestData.parentOrganization(PARENT_HER_ID, "Legekontoret", PARENT_ORG_NUMBER)
        val pasient = Pasient(fnr = "16822449879", fornavn = "Kari", mellomnavn = "Mellom", etternavn = "Nordmann")

        fun person(name: String = "Lege Legesen", postalAddress: PostalAddress? = null) =
            ArTestData.person(CHILD_HER_ID, name, parent, postalAddress = postalAddress)

        describe("toSender") {
            it("maps the parent organization with HER-id and organization number") {
                val sender = person().toSender()

                with(sender.parent) {
                    name shouldBe "Legekontoret"
                    ids shouldBe
                        listOf(
                            OrganizationId(PARENT_HER_ID.toString(), OrganizationIdType.HER_ID),
                            OrganizationId(PARENT_ORG_NUMBER, OrganizationIdType.ENH),
                        )
                }
            }

            it("maps a person to a healthcare professional with its postal address") {
                val sender =
                    person(
                            postalAddress =
                                PostalAddress()
                                    .address("Storgata 1")
                                    .postalBox("Postboks 1")
                                    .postalCode("0001")
                                    .city("Oslo")
                        )
                        .toSender()

                val child = sender.child.shouldBeInstanceOf<MshPersonCommunicationParty>()
                child.ids shouldBe listOf(PersonId(CHILD_HER_ID.toString(), PersonIdType.HER_ID))
                child.firstName shouldBe "Lege"
                child.middleName.shouldBeNull()
                child.lastName shouldBe "Legesen"
                with(child.address.shouldNotBeNull()) {
                    type shouldBe Adressetype.POSTADRESSE
                    streetAdr shouldBe "Storgata 1"
                    postbox shouldBe "Postboks 1"
                    postalCode shouldBe "0001"
                    city shouldBe "Oslo"
                    county.shouldBeNull()
                    country.shouldBeNull()
                }
            }

            it("maps a person without postal address") {
                person().toSender().child.shouldBeInstanceOf<MshPersonCommunicationParty>().address.shouldBeNull()
            }

            it("takes the last word of a person's name as the last name") {
                with(person(name = "Lege  Mellom Legesen ").toSender().child as MshPersonCommunicationParty) {
                    firstName shouldBe "Lege Mellom"
                    lastName shouldBe "Legesen"
                }
            }

            it("rejects a person with a single name") {
                shouldThrow<IllegalStateException> { person(name = "Lege").toSender() }.message shouldBe
                    "Communication party $CHILD_HER_ID has no valid person name"
            }

            it("maps an organization as its own parent") {
                val sender = ArTestData.organization(CHILD_HER_ID, "Avdeling", CHILD_ORG_NUMBER).toSender()

                val ids =
                    listOf(
                        OrganizationId(CHILD_HER_ID.toString(), OrganizationIdType.HER_ID),
                        OrganizationId(CHILD_ORG_NUMBER, OrganizationIdType.ENH),
                    )
                with(sender.parent) {
                    name shouldBe "Avdeling"
                    this.ids shouldBe ids
                }
                with(sender.child.shouldBeInstanceOf<MshOrganizationCommunicationParty>()) {
                    name shouldBe "Avdeling"
                    this.ids shouldBe ids
                }
            }

            it("maps a service as an organization identified by HER-id only") {
                val sender = ArTestData.service(CHILD_HER_ID, "Tjeneste", parent).toSender()

                sender.parent.name shouldBe "Legekontoret"
                with(sender.child.shouldBeInstanceOf<MshOrganizationCommunicationParty>()) {
                    name shouldBe "Tjeneste"
                    ids shouldBe listOf(OrganizationId(CHILD_HER_ID.toString(), OrganizationIdType.HER_ID))
                }
            }

            it("rejects a communication party without parent") {
                shouldThrow<IllegalStateException> {
                        ArTestData.person(CHILD_HER_ID, "Lege Legesen", parent = null).toSender()
                    }
                    .message shouldBe "Communication party $CHILD_HER_ID has no parent organization"
            }
        }

        describe("toReceiver") {
            it("includes the patient") {
                val receiver = person().toReceiver(pasient)

                with(receiver.patient) {
                    fnr shouldBe pasient.fnr
                    firstName shouldBe pasient.fornavn
                    middleName shouldBe pasient.mellomnavn
                    lastName shouldBe pasient.etternavn
                }
                receiver.child.shouldBeInstanceOf<MshPersonCommunicationParty>()
            }
        }
    })
