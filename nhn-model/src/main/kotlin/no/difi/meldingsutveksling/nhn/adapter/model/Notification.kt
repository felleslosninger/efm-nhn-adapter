package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.ks.fiks.nhn.msh.NotificationType as MshNotificationType
import no.ks.fiks.nhn.msh.UnreadNotification

/**
 * The kinds of notifications MSH gives. The JSON holds the name of the constant, e.g. `NEW_MESSAGE`; [value] is what
 * MSH calls it, e.g. `NewMessage`.
 */
@Serializable(with = NotificationTypeSerializer::class)
enum class NotificationType(val value: String) {
    /** A new message to the receiver, given by [Notification.relatedMessageId]. */
    NEW_MESSAGE("NewMessage"),
    /** A message to the receiver was refused by MSH, and has no business document to get. */
    REFUSED_MESSAGE("RefusedMessage"),
    MESSAGE_SENT_STATE_UPDATED("MessageSentStateUpdated"),
    MESSAGE_APPREC_INFO_UPDATED("MessageApprecInfoUpdated"),
    MESSAGE_DELIVERY_STATE_UPDATED("MessageDeliveryStateUpdated"),
    /**
     * A kind this version of the model does not know, e.g. one a newer adapter added later. A client that reads it can
     * skip or delete the notification, and still read the others in the list.
     */
    UNKNOWN(""),
}

/** Reads a kind it does not know as [NotificationType.UNKNOWN], rather than failing the whole list. */
object NotificationTypeSerializer : KSerializer<NotificationType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "no.difi.meldingsutveksling.nhn.adapter.model.NotificationType",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: NotificationType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): NotificationType {
        val name = decoder.decodeString()
        return NotificationType.entries.firstOrNull { it.name == name } ?: NotificationType.UNKNOWN
    }
}

/**
 * An unread notification from MSH, as MSH gives it. It stays unread until it is deleted, see
 * [DeleteNotificationsInput].
 */
@Serializable
data class Notification(
    val notificationId: String,
    val relatedMessageId: String?,
    val type: NotificationType,
    val notificationReceiverHerId: Int,
    val notificationTriggeredByHerId: Int?,
    val description: String?,
    @Serializable(with = OffsetDateTimeSerializer::class) val createdAt: OffsetDateTime?,
)

/** The notifications to delete, at most 1000 at a time. */
@Serializable data class DeleteNotificationsInput(val notificationIds: List<String>)

fun UnreadNotification.toNotification(): Notification =
    Notification(
        notificationId = notificationId,
        relatedMessageId = relatedMessageId,
        type = type.toNotificationType(),
        notificationReceiverHerId = notificationReceiverHerId,
        notificationTriggeredByHerId = notificationTriggeredByHerId,
        description = description,
        createdAt = createdAt,
    )

/** A kind a newer msh-client knows, but this model does not, is passed on as [NotificationType.UNKNOWN]. */
private fun MshNotificationType.toNotificationType(): NotificationType =
    NotificationType.entries.firstOrNull { it != NotificationType.UNKNOWN && it.value == value }
        ?: NotificationType.UNKNOWN
