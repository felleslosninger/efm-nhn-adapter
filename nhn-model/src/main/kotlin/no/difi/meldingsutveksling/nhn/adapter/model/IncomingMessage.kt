package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import no.ks.fiks.nhn.msh.MessageWithMetadata

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

fun MessageWithMetadata.toInMessage(): IncomingMessage =
    IncomingMessage(
        id = id.toString(),
        receiverHerId = receiverHerId,
        senderHerId = senderHerId,
        businessDocumentId = businessDocumentId,
        businessDocumentDate = businessDocumentDate,
        isAppRec = isAppRec,
    )
