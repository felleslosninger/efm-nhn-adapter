package no.difi.meldingsutveksling.nhn.adapter.extensions

import no.difi.meldingsutveksling.nhn.adapter.messages.Pasient
import no.ks.fiks.hdir.Adressetype
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.ar.rest.model.CommunicationParty
import no.ks.fiks.nhn.ar.rest.model.CommunicationPartyType
import no.ks.fiks.nhn.ar.rest.model.ParentOrganization
import no.ks.fiks.nhn.ar.rest.model.PostalAddress
import no.ks.fiks.nhn.msh.Address
import no.ks.fiks.nhn.msh.CommunicationParty as MshCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty as MshOrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonCommunicationParty as MshPersonCommunicationParty
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

fun CommunicationParty.toSender(): Sender = Sender(parentOrganization.toMsh(), toMshChild())

fun CommunicationParty.toReceiver(pasient: Pasient): Receiver =
    Receiver(
        parentOrganization.toMsh(),
        toMshChild(),
        Patient(pasient.fnr, pasient.fornavn, pasient.mellomnavn, pasient.etternavn),
    )

/**
 * The organization a communication party belongs to. Persons and services have a parent organization in
 * Adresseregisteret, while an organization is its own parent.
 */
val CommunicationParty.parentOrganization: ParentOrganization
    get() =
        parentOrganizationOrNull
            ?: throw IllegalStateException("Communication party $herId has no parent organization")

val CommunicationParty.parentOrganizationOrNull: ParentOrganization?
    get() =
        when (type) {
            CommunicationPartyType.PERSON -> personDetails?.parentOrganization
            CommunicationPartyType.SERVICE -> serviceDetails?.parentOrganization
            CommunicationPartyType.ORGANIZATION ->
                ParentOrganization()
                    .herId(herId)
                    .name(name)
                    .organizationNumber(organizationDetails?.organizationNumber)
            null -> null
        }

/** Adresseregisteret has the full name of a person only; the last word is taken as the last name. */
val CommunicationParty.personName: PersonName
    get() {
        val parts = requireName().trim().split(Regex("\\s+"))
        check(parts.size >= 2) { "Communication party $herId has no valid person name" }
        return PersonName(firstName = parts.dropLast(1).joinToString(" "), lastName = parts.last())
    }

data class PersonName(val firstName: String, val lastName: String)

private fun CommunicationParty.toMshChild(): MshCommunicationParty =
    when (type) {
        CommunicationPartyType.PERSON -> toMshPerson()
        CommunicationPartyType.ORGANIZATION ->
            MshOrganizationCommunicationParty(
                name = requireName(),
                ids = organizationIds(herId, organizationDetails?.organizationNumber),
            )
        // MSH has no service type; a service is sent as an organisational unit under its parent.
        CommunicationPartyType.SERVICE ->
            MshOrganizationCommunicationParty(
                name = requireName(),
                ids = organizationIds(herId, organizationNumber = null),
            )
        null -> throw IllegalStateException("Communication party $herId has no type")
    }

private fun ParentOrganization.toMsh(): MshOrganizationCommunicationParty =
    MshOrganizationCommunicationParty(
        name = checkNotNull(name) { "Parent organization $herId has no name" },
        ids = organizationIds(herId, organizationNumber),
    )

private fun organizationIds(herId: Int, organizationNumber: String?): List<OrganizationId> =
    listOfNotNull(
        OrganizationId(herId.toString(), OrganizationIdType.HER_ID),
        organizationNumber?.let { OrganizationId(it, OrganizationIdType.ENH) },
    )

private fun CommunicationParty.toMshPerson(): MshPersonCommunicationParty {
    val name = personName
    return MshPersonCommunicationParty(
        ids = listOf(PersonId(herId.toString(), PersonIdType.HER_ID)),
        address = postalAddress?.toMsh(),
        firstName = name.firstName,
        lastName = name.lastName,
        middleName = null,
    )
}

private fun CommunicationParty.requireName(): String =
    checkNotNull(name?.takeIf { it.isNotBlank() }) { "Communication party $herId has no name" }

private fun PostalAddress.toMsh(): Address =
    Address(
        type = Adressetype.POSTADRESSE,
        streetAdr = address,
        postalCode = postalCode,
        city = city,
        postbox = postalBox,
        county = null,
        country = null,
    )
