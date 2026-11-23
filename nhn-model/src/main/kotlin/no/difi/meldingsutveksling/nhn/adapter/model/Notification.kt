package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import no.ks.fiks.nhn.msh.NotificationType as MshNotificationType
import no.ks.fiks.nhn.msh.UnreadNotification

/** The kinds of notifications MSH gives, with the values MSH uses for them. */
@Serializable
enum class NotificationType(val value: String) {
    /** A new message to the receiver, given by [Notification.relatedMessageId]. */
    NEW_MESSAGE("NewMessage"),
    /** A message to the receiver was refused by MSH, and has no business document to get. */
    REFUSED_MESSAGE("RefusedMessage"),
    MESSAGE_SENT_STATE_UPDATED("MessageSentStateUpdated"),
    MESSAGE_APPREC_INFO_UPDATED("MessageApprecInfoUpdated"),
    MESSAGE_DELIVERY_STATE_UPDATED("MessageDeliveryStateUpdated"),
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

private fun MshNotificationType.toNotificationType(): NotificationType =
    NotificationType.entries.first { it.value == value }
