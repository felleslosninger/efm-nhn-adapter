package no.difi.meldingsutveksling.nhn.adapter.model

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding

@Serializable
data class IncomingApplicationReceipt(
    val acknowledgedId: String, // Adjust to UUID if needed
    @Serializable(with = StatusForMottakAvMeldingSerializer::class) val status: StatusForMottakAvMelding,
    val errors: List<IncomingApplicationReceiptError>,
)

@Serializable
data class OutgoingApplicationReceipt
@OptIn(ExperimentalUuidApi::class)
constructor(
    val acknowledgedId: String,
    val senderHerId: Int,
    @Serializable(with = StatusForMottakAvMeldingSerializer::class) val status: StatusForMottakAvMelding,
    val errors: List<OutgoingApplicationReceiptError>? = null,
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

@Serializable
data class IncomingApplicationReceiptError(
    @Serializable(with = FeilmeldingForApplikasjonskvitteringSerializer::class)
    val type: FeilmeldingForApplikasjonskvittering,
    val details: String? = null,
    val errorCode: String? = null,
    val description: String? = null,
    val oid: String? = null,
)

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

@OptIn(ExperimentalUuidApi::class)
object UUIDSerializer : KSerializer<Uuid> {
    override val descriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Uuid {
        return Uuid.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Uuid) {
        encoder.encodeString(value.toString())
    }
}

@OptIn(ExperimentalUuidApi::class)
fun OutgoingApplicationReceipt.toOriginal(): no.ks.fiks.nhn.msh.OutgoingApplicationReceipt =
    no.ks.fiks.nhn.msh.OutgoingApplicationReceipt(
        acknowledgedId = UUID.fromString(this.acknowledgedId),
        senderHerId = this.senderHerId,
        status = this.status,
        errors = this.errors?.map { it.toOriginal() },
    )

@Serializable
data class OutgoingApplicationReceiptError(
    @Serializable(with = FeilmeldingForApplikasjonskvitteringSerializer::class)
    val type: FeilmeldingForApplikasjonskvittering,
    val details: String? = null,
)

fun OutgoingApplicationReceiptError.toOriginal(): no.ks.fiks.nhn.msh.OutgoingApplicationReceiptError =
    no.ks.fiks.nhn.msh.OutgoingApplicationReceiptError(type = this.type, details = this.details)

fun no.ks.fiks.nhn.msh.IncomingApplicationReceiptError.toSerializable(): IncomingApplicationReceiptError =
    IncomingApplicationReceiptError(
        type = this.type,
        details = this.details,
        errorCode = this.errorCode,
        description = this.description,
        oid = this.oid,
    )

fun no.ks.fiks.nhn.msh.IncomingApplicationReceipt.toSerializable(): IncomingApplicationReceipt =
    IncomingApplicationReceipt(
        acknowledgedId = this.acknowledgedBusinessDocumentId,
        status = this.status,
        errors = this.errors.map { it.toSerializable() },
    )
