package no.difi.meldingsutveksling.nhn.adapter

import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty

fun mockFastlegeCommunicationParty(herId2: Int, herId1: Int, orgnumber: String) =
    PersonCommunicationParty(
        herId2,
        "Fastlege",
        CommunicationPartyParent(herId1, "ParentComunicationParty", orgnumber),
        listOf(),
        listOf(),
        "Peter",
        "",
        "Petterson",
    )

fun mockNhnServiceCommunicationParty(herId2: Int, herId1: Int, orgnumber: String) =
    OrganizationCommunicationParty(
        herId2,
        "Nhn service",
        CommunicationPartyParent(herId1, "ParentComunicationParty", orgnumber),
        listOf(),
        listOf(),
        orgnumber,
    )
