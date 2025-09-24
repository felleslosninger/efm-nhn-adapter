package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.ApplicationReceiptInfo
import no.nhn.msh.v2.model.StatusInfo

@Serializable
sealed interface CommunicationParty {
    val herid1: String
    val herid2: String
}

@Serializable
@SerialName("Sender")
data class Sender(override val herid1: String, override val herid2: String, val name: String) : CommunicationParty

@Serializable
@SerialName("Receiver")
data class Receiver(override val herid1: String, override val herid2: String) : CommunicationParty

@Serializable
data class MessageOut(
    val messageId: String,
    val conversationId: String,
    val onBehalfOfOrgNum: String,
    val sender: Sender,
    val receiver: Receiver,
    val fagmelding: String,
    val patient: Patient,
)

@Serializable
data class Fagmelding(val subject: String, val body: String, val healthcareProfessional: HealthcareProfessional)

@Serializable
data class Person(
    val fnr: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val phoneNumber: String?,
)

typealias HealthcareProfessional = Person

typealias Patient = Person

@Serializable
data class ArDetails(
    val herid1: Int,
    val communicationPartyParentName: String,
    val orgNumber: String,
    val herid2: Int,
    val communicationPartyName: String,
    val ediAdress: String,
    val pemDigdirSertifikat: String,
)

fun no.ks.fiks.nhn.msh.StatusInfo.toMessageStatus(): MessageStatus =
    MessageStatus(
        this.receiverHerId,
        TransportStatus.fromValue(this.deliveryState.name),
        this.appRecStatus?.let { ApprecStatus.fromValue(it.name) },
    )

@Serializable
data class MessageStatus(
    val receiverHerId: Int,
    val transportStatus: TransportStatus,
    val apprecStatus: ApprecStatus? = null,
)

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
enum class TransportStatus(val value: String) {
    UNCONFIRMED("Unconfirmed"),
    ACKNOWLEDGED("Acknowledged"),
    REJECTED("Rejected");

    companion object {
        fun fromValue(value: String?): TransportStatus {
            for (b in TransportStatus.values()) {
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
