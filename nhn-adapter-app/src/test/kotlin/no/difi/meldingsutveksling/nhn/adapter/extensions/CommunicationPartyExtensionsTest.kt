package no.difi.meldingsutveksling.nhn.adapter.extensions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.Country
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.ar.PhysicalAddress
import no.ks.fiks.nhn.ar.PostalAddressType
import no.ks.fiks.nhn.ar.ServiceCommunicationParty
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
        val parent = CommunicationPartyParent(PARENT_HER_ID, "Legekontoret", PARENT_ORG_NUMBER)
        val pasient = Pasient(fnr = "16822449879", fornavn = "Kari", mellomnavn = "Mellom", etternavn = "Nordmann")

        fun person(physicalAddresses: List<PhysicalAddress> = emptyList(), parent: CommunicationPartyParent? = null) =
            PersonCommunicationParty(
                herId = CHILD_HER_ID,
                name = "Lege Legesen",
                parent = parent,
                physicalAddresses = physicalAddresses,
                electronicAddresses = emptyList(),
                firstName = "Lege",
                middleName = null,
                lastName = "Legesen",
            )

        describe("toSender") {
            it("maps the parent organization with HER-id and organization number") {
                val sender = person(parent = parent).toSender()

                with(sender.parent) {
                    name shouldBe "Legekontoret"
                    ids shouldBe
                        listOf(
                            OrganizationId(PARENT_HER_ID.toString(), OrganizationIdType.HER_ID),
                            OrganizationId(PARENT_ORG_NUMBER, OrganizationIdType.ENH),
                        )
                }
            }

            it("maps a person to a healthcare professional with the address of the first physical address") {
                val sender =
                    person(
                            parent = parent,
                            physicalAddresses =
                                listOf(
                                    PhysicalAddress(
                                        type = PostalAddressType.BESOKSADRESSE,
                                        streetAddress = "Storgata 1",
                                        postbox = null,
                                        postalCode = "0001",
                                        city = "Oslo",
                                        country = Country("NO", "Norge"),
                                    ),
                                    PhysicalAddress(
                                        type = PostalAddressType.POSTADRESSE,
                                        streetAddress = null,
                                        postbox = "Postboks 1",
                                        postalCode = "0002",
                                        city = "Oslo",
                                        country = null,
                                    ),
                                ),
                        )
                        .toSender()

                val child = sender.child.shouldBeInstanceOf<MshPersonCommunicationParty>()
                child.ids shouldBe listOf(PersonId(CHILD_HER_ID.toString(), PersonIdType.HER_ID))
                child.firstName shouldBe "Lege"
                child.middleName.shouldBeNull()
                child.lastName shouldBe "Legesen"
                with(child.address.shouldNotBeNull()) {
                    type shouldBe Adressetype.BESOKSADRESSE
                    streetAdr shouldBe "Storgata 1"
                    postalCode shouldBe "0001"
                    city shouldBe "Oslo"
                    postbox.shouldBeNull()
                    county.shouldBeNull()
                    country.shouldNotBeNull().code shouldBe "NO"
                    country.shouldNotBeNull().name shouldBe "Norge"
                }
            }

            it("maps a postal address without country") {
                val sender =
                    person(
                            parent = parent,
                            physicalAddresses =
                                listOf(
                                    PhysicalAddress(
                                        type = PostalAddressType.POSTADRESSE,
                                        streetAddress = null,
                                        postbox = "Postboks 1",
                                        postalCode = "0002",
                                        city = "Oslo",
                                        country = null,
                                    )
                                ),
                        )
                        .toSender()

                with(sender.child.shouldBeInstanceOf<MshPersonCommunicationParty>().address.shouldNotBeNull()) {
                    type shouldBe Adressetype.POSTADRESSE
                    postbox shouldBe "Postboks 1"
                    country.shouldBeNull()
                }
            }

            it("maps an organization child with its organization number") {
                val sender =
                    OrganizationCommunicationParty(
                            herId = CHILD_HER_ID,
                            name = "Avdeling",
                            parent = parent,
                            physicalAddresses = emptyList(),
                            electronicAddresses = emptyList(),
                            organizationNumber = CHILD_ORG_NUMBER,
                        )
                        .toSender()

                with(sender.child.shouldBeInstanceOf<MshOrganizationCommunicationParty>()) {
                    name shouldBe "Avdeling"
                    ids shouldBe
                        listOf(
                            OrganizationId(CHILD_HER_ID.toString(), OrganizationIdType.HER_ID),
                            OrganizationId(CHILD_ORG_NUMBER, OrganizationIdType.ENH),
                        )
                }
            }

            it("maps a service as an organization identified by HER-id only") {
                val sender =
                    ServiceCommunicationParty(
                            herId = CHILD_HER_ID,
                            name = "Tjeneste",
                            parent = parent,
                            physicalAddresses = emptyList(),
                            electronicAddresses = emptyList(),
                        )
                        .toSender()

                with(sender.child.shouldBeInstanceOf<MshOrganizationCommunicationParty>()) {
                    name shouldBe "Tjeneste"
                    ids shouldBe listOf(OrganizationId(CHILD_HER_ID.toString(), OrganizationIdType.HER_ID))
                }
            }

            it("rejects a communication party without parent") {
                shouldThrow<IllegalStateException> { person().toSender() }.message shouldBe
                    "Communication party $CHILD_HER_ID has no parent organization"
            }
        }

        describe("toReceiver") {
            it("includes the patient") {
                val receiver = person(parent = parent).toReceiver(pasient)

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
