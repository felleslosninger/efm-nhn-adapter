package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable
import no.ks.fiks.nhn.msh.StatusInfo

@Serializable
data class MessageStatus(
    val receiverHerId: Int,
    val transportStatus: TransportStatus,
    val apprecStatus: ApplicationReceiptStatus? = null,
)

enum class ApplicationReceiptStatus(val value: String) {
    OK("Ok"),
    REJECTED("Rejected"),
    OK_ERROR_IN_MESSAGE_PART("OkErrorInMessagePart");

    companion object {
        fun fromValue(value: String?): ApplicationReceiptStatus = entries.fromValue(value) { it.value }
    }
}

@Serializable
enum class TransportStatus(val value: String) {
    UNCONFIRMED("Unconfirmed"),
    ACKNOWLEDGED("Acknowledged"),
    REJECTED("Rejected");

    companion object {
        fun fromValue(value: String?): TransportStatus = entries.fromValue(value) { it.value }
    }
}

fun StatusInfo.toMessageStatus(): MessageStatus =
    MessageStatus(
        receiverHerId = receiverHerId,
        transportStatus = TransportStatus.fromValue(deliveryState.name),
        apprecStatus = appRecStatus?.let { ApplicationReceiptStatus.fromValue(it.name) },
    )

/**
 * Matches on either the display value (e.g. "OkErrorInMessagePart") or the constant name (OK_ERROR_IN_MESSAGE_PART).
 */
private fun <E : Enum<E>> List<E>.fromValue(value: String?, displayValue: (E) -> String): E =
    firstOrNull { displayValue(it).equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unexpected value '$value'")
