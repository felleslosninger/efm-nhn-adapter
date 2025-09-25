package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.ApplicationReceiptInfo

@Serializable
enum class ApprecStatus(val value: String) {
    OK("Ok"),
    REJECTED("Rejected"),
    OK_ERROR_IN_MESSAGE_PART("OkErrorInMessagePart");

    companion object {
        fun fromValue(value: String?): ApprecStatus {
            for (b in ApprecStatus.values()) {
                if (b.value.equals(value, true)) {
                    return b
                }
            }
            throw IllegalArgumentException("Unexpected value '$value'")
        }
    }
}

@Serializable
data class SerializableApplicationReceiptInfo(
    val recieverHerId: Int,
    @Serializable(with = StatusForMottakAvMeldingSerializer::class) val status: StatusForMottakAvMelding?,
    val errors: List<SerializableApplicationReceiptError>,
)

@Serializable
data class SerializableApplicationReceiptError(
    @Serializable(with = FeilmeldingForApplikasjonskvitteringSerializer::class)
    val type: FeilmeldingForApplikasjonskvittering,
    val details: String? = null,
)

fun ApplicationReceiptInfo.toSerializable(): SerializableApplicationReceiptInfo =
    SerializableApplicationReceiptInfo(this.receiverHerId, this.status, this.errors.map { it.toSerializable() })

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

object FeilmeldingForApplikasjonskvitteringSerializer : KSerializer<FeilmeldingForApplikasjonskvittering> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FeilmeldingForApplikasjonskvittering", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FeilmeldingForApplikasjonskvittering) {
        encoder.encodeString(value.verdi)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(decoder: Decoder): FeilmeldingForApplikasjonskvittering {
        val verdi = decoder.decodeString()
        return FeilmeldingForApplikasjonskvittering.entries.find { it.verdi == verdi }
            ?: throw IllegalArgumentException("Unknown FeilmeldingForApplikasjonskvittering verdi: $verdi")
    }
}
