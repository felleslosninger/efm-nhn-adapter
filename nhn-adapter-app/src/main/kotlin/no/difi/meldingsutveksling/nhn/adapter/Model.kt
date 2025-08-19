package no.difi.meldingsutveksling.nhn.adapter

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
@SerialName("Reciever")
data class Reciever(override val herid1: String, override val herid2: String) : CommunicationParty

@Serializable
data class MessageOut(
    val messageId: String,
    val conversationId: String,
    val sender: Sender,
    val reciever: Reciever,
    val fagmelding: String,
    val patient: Patient,
    val healthcareProfressional: HealthcareProfressional,
)

@Serializable
data class Fagmelding(val subject: String, val body: String, val healthcareProfressional: HealthcareProfressional)

@Serializable
data class Person(val firstName: String, val middleName: String?, val lastName: String, val phoneNumber: String)

typealias HealthcareProfressional = Person

typealias Patient = Person

@Serializable
data class ArDetails(val herid1: Int, val herid2: Int, val ediAdress: String, val pemDigdirSertifikat: String)
