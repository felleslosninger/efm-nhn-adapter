package no.difi.meldingsutveksling.nhn.adapter.extensions

import no.difi.meldingsutveksling.nhn.adapter.model.Pasient
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.Country
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.ar.PhysicalAddress
import no.ks.fiks.nhn.ar.PostalAddressType
import no.ks.fiks.nhn.msh.Address
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

fun CommunicationParty.toSender(): Sender = Sender(parent!!.toMsh(), toMshChild())

fun CommunicationParty.toReceiver(pasient: Pasient): Receiver =
    Receiver(
        parent!!.toMsh(),
        toMshChild(),
        Patient(pasient.fnr, pasient.fornavn, pasient.mellomnavn, pasient.etternavn),
    )

private fun CommunicationParty.toMshChild(): no.ks.fiks.nhn.msh.CommunicationParty =
    when (this) {
        is OrganizationCommunicationParty -> {
            no.ks.fiks.nhn.msh.OrganizationCommunicationParty(
                name = name,
                ids =
                    organizationNumber?.let {
                        listOf(
                            OrganizationId(herId.toString(), OrganizationIdType.HER_ID),
                            OrganizationId(it, OrganizationIdType.ENH),
                        )
                    } ?: listOf(OrganizationId(herId.toString(), OrganizationIdType.HER_ID)),
            )
        }
        is PersonCommunicationParty -> {
            this.toMsh()
        }
        else -> {
            throw IllegalArgumentException("Unknown communication party type: $this")
        }
    }

private fun CommunicationPartyParent.toMsh(): no.ks.fiks.nhn.msh.OrganizationCommunicationParty =
    no.ks.fiks.nhn.msh.OrganizationCommunicationParty(
        name = name,
        ids =
            listOf(
                OrganizationId(herId.toString(), OrganizationIdType.HER_ID),
                OrganizationId(organizationNumber, OrganizationIdType.ENH),
            ),
    )

private fun PersonCommunicationParty.toMsh(): no.ks.fiks.nhn.msh.PersonCommunicationParty =
    no.ks.fiks.nhn.msh.PersonCommunicationParty(
        ids = listOf(PersonId(herId.toString(), PersonIdType.HER_ID)),
        address = physicalAddresses.map { it.toMsh() }.firstOrNull(),
        firstName = firstName,
        lastName = lastName,
        middleName = middleName,
    )

private fun PhysicalAddress.toMsh(): Address =
    Address(
        type = type.toMsh(),
        streetAdr = streetAddress,
        postalCode = postalCode,
        city = city,
        postbox = postbox,
        county = null,
        country = country?.toMsh(),
    )

private fun PostalAddressType.toMsh(): Adressetype =
    when (this) {
        PostalAddressType.POSTADRESSE -> Adressetype.POSTADRESSE
        PostalAddressType.BESOKSADRESSE -> Adressetype.BESOKSADRESSE
    }

private fun Country.toMsh(): no.ks.fiks.nhn.msh.Country = no.ks.fiks.nhn.msh.Country(code = code, name = name)
