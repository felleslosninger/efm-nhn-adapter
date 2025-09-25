package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageOut(
    val messageId: String,
    val conversationId: String,
    val onBehalfOfOrgNum: String,
    val sender: Sender,
    val receiver: Receiver,
    val fagmelding: String,
    val patient: Patient,
)

@Serializable
data class Fagmelding(val subject: String, val body: String, val healthcareProfessional: HealthcareProfessional)

@Serializable
data class Person(
    val fnr: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val phoneNumber: String?,
)

typealias HealthcareProfessional = Person

typealias Patient = Person
