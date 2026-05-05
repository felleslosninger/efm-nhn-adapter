package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.IncomingVedlegg
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

@Serializable
data class OutgoingBusinessDocument(
    val messageId: String,
    val conversationId: String?,
    val parentId: String?,
    val senderHerId: Int,
    val receiverHerId: Int,
    val payload: DialogmeldingMessage
)

@Serializable
data class IncomingBusinessDocument(
    val id: String,
    val senderHerId: Int,
    val receiverHerId: Int,
    val conversationId: String?,
    val parentId: String?,
    val payload: DialogmeldingMessage
)

@Serializable
data class DialogmeldingMessage(
    val hoveddokument: String,
    val pasient: Pasient?,
    val metadataFiler: Map<String, String?>
)

@Serializable
data class Pasient(val fnr: String, val fornavn: String, val mellomnavn: String?, val etternavn: String)

fun no.ks.fiks.nhn.msh.Patient.toSerializable() =
    Pasient(this.fnr, this.firstName, this.middleName, this.lastName)


data class BusinessDocumentResponse(
    val id: String,
    val type: String,
    val sender: Sender,
    val receiver: Receiver,
    val dialogmelding: Dialogmelding,
    val vedlegg: List<IncomingVedlegg>,
    val conversationRef: ConversationRef?,
)


fun BusinessDocumentResponse.toSerializable(): IncomingBusinessDocument {

    val metadataFiler =
        this.vedlegg
            .mapIndexed { index: Int, attachment: IncomingVedlegg -> AttachmentNames.vedlegg(index) to attachment.description }
            .toMap()

    return IncomingBusinessDocument(
        this.id,
        this.sender.child.herId!!,
        this.receiver.child.herId!!,
        this.conversationRef?.refToConversation,
        this.conversationRef?.refToParent,
        DialogmeldingMessage(
            AttachmentNames.DIALOGMELDING,
            this.receiver.patient.toSerializable(),
            metadataFiler
        )
    )
}
