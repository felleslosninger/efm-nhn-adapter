package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MessageOut {

    abstract val messageId: String
    abstract val conversationId: String
    abstract val onBehalfOfOrgNum: String
    abstract val sender: Sender
    abstract val receiver: Receiver
    abstract val fagmelding: EncryptedFagmelding
    abstract val vedlegg: String

    @Serializable
    @SerialName("Unsigned")
    data class Unsigned(
        override val messageId: String,
        override val conversationId: String,
        override val onBehalfOfOrgNum: String,
        override val sender: Sender,
        override val receiver: Receiver,
        override val fagmelding: EncryptedFagmelding,
        override val vedlegg: String,
    ) : MessageOut()

    @Serializable
    @SerialName("Signed")
    data class Signed(
        override val messageId: String,
        override val conversationId: String,
        override val onBehalfOfOrgNum: String,
        override val sender: Sender,
        override val receiver: Receiver,
        override val fagmelding: EncryptedFagmelding,
        override val vedlegg: String,
        val signature: Signature,
    ) : MessageOut()
}


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
