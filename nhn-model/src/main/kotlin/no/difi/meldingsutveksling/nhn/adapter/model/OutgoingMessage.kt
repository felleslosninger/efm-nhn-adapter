package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class OutgoingMessage(
    val messageId: String,
    val conversationId: String,
    val senderHerId: Int,
    val receiverHerId: Int,
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
    val fornavn: String,
    val mellomnavn: String? = null,
    val etternavn: String,
    val phoneNumber: String? = null,
)

typealias Pasient = Person
