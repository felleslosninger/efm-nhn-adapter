package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class ArDetails(
    val herId1: Int,
    val communicationPartyParentName: String,
    val orgNumber: String,
    val herId2: Int,
    val communicationPartyName: String,
    val ediAddress: String,
    val derCertificate: String,
)
