package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageOut(
    val messageId: String,
    val conversationId: String,
    val onBehalfOfOrgNum: String,
    val sender: Sender,
    val receiver: Receiver,
    val fagmelding: EncryptedFagmelding,
    val vedlegg: String,
    val signature: Signature,
)

@Serializable data class Signature(val alg: String, val kid: String? = null, val value: String)

@Serializable data class EncryptedFagmelding(val base64DerEncryptionCertificate: String, val message: String)

@Serializable
data class Fagmelding(
    val notat: Notat,
    val patient: Patient,
    val responsibleHealthcareProfessionalId: String,
    val vedleggBeskrivelse: String,
)

@Serializable data class Notat(val subject: String, val notatinnhold: String)

@Serializable
data class Person(
    val fnr: String,
    val firstName: String,
    val middleName: String? = null,
    val lastName: String,
    val phoneNumber: String? = null,
)

typealias HealthcareProfessional = Person

typealias Patient = Person
