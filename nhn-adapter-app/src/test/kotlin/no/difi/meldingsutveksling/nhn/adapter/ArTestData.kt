package no.difi.meldingsutveksling.nhn.adapter

import no.ks.fiks.nhn.ar.rest.model.CommunicationParty
import no.ks.fiks.nhn.ar.rest.model.CommunicationPartyType
import no.ks.fiks.nhn.ar.rest.model.OrganizationDetails
import no.ks.fiks.nhn.ar.rest.model.ParentOrganization
import no.ks.fiks.nhn.ar.rest.model.PersonDetails
import no.ks.fiks.nhn.ar.rest.model.PostalAddress
import no.ks.fiks.nhn.ar.rest.model.ServiceDetails

/** Builders for the communication parties returned by the Adresseregisteret REST client. */
object ArTestData {
    fun parentOrganization(herId: Int, name: String, organizationNumber: String?): ParentOrganization =
        ParentOrganization().herId(herId).name(name).organizationNumber(organizationNumber)

    fun organization(herId: Int, name: String, organizationNumber: String?): CommunicationParty =
        CommunicationParty()
            .herId(herId)
            .name(name)
            .type(CommunicationPartyType.ORGANIZATION)
            .organizationDetails(OrganizationDetails().organizationNumber(organizationNumber))

    fun service(herId: Int, name: String, parent: ParentOrganization): CommunicationParty =
        CommunicationParty()
            .herId(herId)
            .name(name)
            .type(CommunicationPartyType.SERVICE)
            .serviceDetails(ServiceDetails().parentOrganization(parent))

    fun person(
        herId: Int,
        name: String,
        parent: ParentOrganization?,
        phoneNumber: String? = null,
        postalAddress: PostalAddress? = null,
    ): CommunicationParty =
        CommunicationParty()
            .herId(herId)
            .name(name)
            .type(CommunicationPartyType.PERSON)
            .personDetails(parent?.let { PersonDetails().parentOrganization(it) })
            .phoneNumber(phoneNumber)
            .postalAddress(postalAddress)
}
