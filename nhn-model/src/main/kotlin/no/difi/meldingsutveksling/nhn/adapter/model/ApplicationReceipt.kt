package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.ks.fiks.hdir.StatusForMottakAvMelding

@Serializable
data class IncomingApplicationReceipt(
    val id: String,
    val rawReceipt: String,
    val payload: DialogmeldingKvitteringMessage,
)

@Serializable
data class OutgoingApplicationReceipt
@OptIn(ExperimentalUuidApi::class)
constructor(
    val senderHerId: Int,
    val payload: DialogmeldingKvitteringMessage,
)

@Serializable
data class DialogmeldingKvitteringMessage(
    val relatedToMessageId: String,
    val status: DialogmeldingKvitteringStatus,
    val messages: List<KvitteringStatusMessage>?,
    val hoveddokument: String? = null
)

enum class DialogmeldingKvitteringStatus {
    OK,
    REJECTED,
    OK_ERROR_IN_MESSAGE_PART
}

@Serializable
data class KvitteringStatusMessage(
    val code: String,
    val text: String,
)

@Serializable
enum class ApplicationReceiptStatus(val value: String) {
    OK("Ok"),
    REJECTED("Rejected"),
    OK_ERROR_IN_MESSAGE_PART("OkErrorInMessagePart");

    companion object {
        fun fromValue(value: String?): ApplicationReceiptStatus {
            for (b in entries) {
                if (b.value.equals(value, true)) {
                    return b
                }
            }
            throw IllegalArgumentException("Unexpected value '$value'")
        }
    }
}

object StatusForMottakAvMeldingSerializer : KSerializer<StatusForMottakAvMelding> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StatusForMottakAvMelding", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StatusForMottakAvMelding) {
        encoder.encodeString(value.verdi)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(decoder: Decoder): StatusForMottakAvMelding {
        val verdi = decoder.decodeString()
        return StatusForMottakAvMelding.entries.find { it.verdi == verdi }
            ?: throw IllegalArgumentException("Unknown StatusForMottakAvMelding verdi: $verdi")
    }
}

