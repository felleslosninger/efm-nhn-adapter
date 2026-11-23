package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import no.ks.fiks.nhn.msh.MessageWithMetadata

/** MsgType of an application receipt (AppRec) business document. */
const val APPREC_MSG_TYPE = "APPREC"

@Serializable data class GetDocumentInput(val id: String)

@Serializable
data class IncomingMessage(
    val id: String,
    val receiverHerId: Int,
    val senderHerId: Int,
    val businessDocumentId: String,
    @Serializable(with = OffsetDateTimeSerializer::class) val businessDocumentDate: OffsetDateTime?,
    val isAppRec: Boolean,
)

/**
 * Maps the metadata of a message received by [receiverHerId].
 *
 * MSH lists every receiver of a message, while the client fetches the messages of one receiver at a time: the message
 * is therefore given to the client as received by [receiverHerId], which must be one of the receivers. Whether the
 * message is an application receipt follows from the MsgType of its business document.
 */
fun MessageWithMetadata.toInMessage(receiverHerId: Int): IncomingMessage {
    require(receiverHerIds?.contains(receiverHerId) != false) {
        "Message $id is not received by $receiverHerId, but by $receiverHerIds"
    }
    return IncomingMessage(
        id = id,
        receiverHerId = receiverHerId,
        senderHerId = requireNotNull(senderHerId) { "Message $id is missing senderHerId" },
        businessDocumentId = requireNotNull(businessDocumentId) { "Message $id is missing businessDocumentId" },
        businessDocumentDate = businessDocumentDate,
        isAppRec =
            requireNotNull(businessDocumentMsgType) { "Message $id is missing businessDocumentMsgType" }
                .equals(APPREC_MSG_TYPE, ignoreCase = true),
    )
}
