package no.difi.meldingsutveksling.nhn.adapter.extensions

import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
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
import no.ks.fiks.nhn.ar.ServiceCommunicationParty
import no.ks.fiks.nhn.msh.Address
import no.ks.fiks.nhn.msh.CommunicationParty as MshCommunicationParty
import no.ks.fiks.nhn.msh.Country as MshCountry
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty as MshOrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonCommunicationParty as MshPersonCommunicationParty
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

fun CommunicationParty.toSender(): Sender = Sender(requireParent().toMsh(), toMshChild())

fun CommunicationParty.toReceiver(pasient: Pasient): Receiver =
    Receiver(
        requireParent().toMsh(),
        toMshChild(),
        Patient(pasient.fnr, pasient.fornavn, pasient.mellomnavn, pasient.etternavn),
    )

private fun CommunicationParty.requireParent(): CommunicationPartyParent =
    checkNotNull(parent) { "Communication party $herId has no parent organization" }

private fun CommunicationParty.toMshChild(): MshCommunicationParty =
    when (this) {
        is OrganizationCommunicationParty ->
            MshOrganizationCommunicationParty(name = name, ids = organizationIds(herId, organizationNumber))
        is PersonCommunicationParty -> toMsh()
        // MSH has no service type; a service is sent as an organisational unit under its parent.
        is ServiceCommunicationParty ->
            MshOrganizationCommunicationParty(name = name, ids = organizationIds(herId, organizationNumber = null))
    }

private fun CommunicationPartyParent.toMsh(): MshOrganizationCommunicationParty =
    MshOrganizationCommunicationParty(name = name, ids = organizationIds(herId, organizationNumber))

private fun organizationIds(herId: Int, organizationNumber: String?): List<OrganizationId> =
    listOfNotNull(
        OrganizationId(herId.toString(), OrganizationIdType.HER_ID),
        organizationNumber?.let { OrganizationId(it, OrganizationIdType.ENH) },
    )

private fun PersonCommunicationParty.toMsh(): MshPersonCommunicationParty =
    MshPersonCommunicationParty(
        ids = listOf(PersonId(herId.toString(), PersonIdType.HER_ID)),
        address = physicalAddresses.firstOrNull()?.toMsh(),
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

private fun Country.toMsh(): MshCountry = MshCountry(code = code, name = name)
