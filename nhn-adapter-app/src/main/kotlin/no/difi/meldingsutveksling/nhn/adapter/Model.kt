package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
