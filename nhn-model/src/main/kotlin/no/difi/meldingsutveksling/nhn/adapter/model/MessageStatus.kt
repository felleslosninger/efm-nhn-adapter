package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageStatus(
    val receiverHerId: Int,
    val transportStatus: TransportStatus,
    val apprecStatus: ApprecStatus? = null,
)

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

fun no.ks.fiks.nhn.msh.StatusInfo.toMessageStatus(): MessageStatus =
    MessageStatus(
        this.receiverHerId,
        TransportStatus.fromValue(this.deliveryState.name),
        this.appRecStatus?.let { ApprecStatus.fromValue(it.name) },
    )
