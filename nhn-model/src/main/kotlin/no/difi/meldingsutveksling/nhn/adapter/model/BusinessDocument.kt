package no.difi.meldingsutveksling.nhn.adapter.model

import java.io.InputStream
import kotlin.time.Instant
import kotlinx.serialization.Serializable

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
    val metadataFiler: Map<String, AttachmentMetadata>
)

data class IncomingAttachment(
    val issueDate: Instant?,
    val description: String?,
    val mimeType: String,
    val data: InputStream?,
)

@Serializable
data class AttachmentMetadata(
    val issueDate: String?,
    val description: String?,
)


@Serializable
data class Pasient(val fnr: String, val fornavn: String, val mellomnavn: String?, val etternavn: String)

fun no.ks.fiks.nhn.msh.Patient.toSerializable() =
    Pasient(this.fnr, this.firstName, this.middleName, this.lastName)



