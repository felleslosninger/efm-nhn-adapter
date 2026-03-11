package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommunicationParty {
    val herid1: String
    val herid2: String
}

@Serializable
@SerialName("Sender")
data class Sender(override val herid1: String, override val herid2: String, val name: String) : CommunicationParty

@Serializable
@SerialName("Receiver")
data class Receiver(override val herid1: String, override val herid2: String, val patientFnr: String?) :
    CommunicationParty
