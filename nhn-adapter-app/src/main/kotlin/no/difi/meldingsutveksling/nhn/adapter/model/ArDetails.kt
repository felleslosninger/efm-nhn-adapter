package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class ArDetails(
    val herid1: Int,
    val communicationPartyParentName: String,
    val orgNumber: String,
    val herid2: Int,
    val communicationPartyName: String,
    val ediAdress: String,
    val derDigdirSertifikat: String,
)
