package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class ArDetails(
    val parentHerId1: Int,
    val parentName: String,
    val orgNumber: String,
    val herId: Int,
    val name: String,
    val derCertificate: String,
)
