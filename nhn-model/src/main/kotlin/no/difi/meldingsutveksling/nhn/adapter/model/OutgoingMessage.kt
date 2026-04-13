package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class OutgoingMessage(
    val messageId: String,
    val conversationId: String,
    val sender: String,
    val receiver: String,
    val payload: DialogmeldingMessage
)

@Serializable
data class DialogmeldingMessage (
    val pasient: Pasient,
    val fastlege: Int,
    val metadataFiler: Map<String, String>
)

@Serializable
data class Dialogmelding(
    val notat: Notat
)

@Serializable
data class Notat(
    val temaBeskrivelse: String,
    val innhold: String
)

@Serializable
data class Person(
    val fnr: String,
    val firstName: String,
    val middleName: String? = null,
    val lastName: String,
    val phoneNumber: String? = null,
)

typealias Pasient = Person
