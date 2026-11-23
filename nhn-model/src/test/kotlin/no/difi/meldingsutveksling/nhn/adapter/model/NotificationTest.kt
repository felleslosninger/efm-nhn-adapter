package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.KxJson
import no.ks.fiks.nhn.msh.NotificationType as MshNotificationType
import no.ks.fiks.nhn.msh.UnreadNotification

class NotificationTest :
    DescribeSpec({
        val createdAt = OffsetDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.ofHours(2))
        val notification =
            Notification(
                notificationId = "notification-1",
                relatedMessageId = "message-1",
                type = NotificationType.NEW_MESSAGE,
                notificationReceiverHerId = 1000002,
                notificationTriggeredByHerId = 2000002,
                description = "New message",
                createdAt = createdAt,
            )

        describe("UnreadNotification.toNotification") {
            it("maps the notification from MSH") {
                UnreadNotification(
                        notificationId = "notification-1",
                        relatedMessageId = "message-1",
                        type = MshNotificationType.NEW_MESSAGE,
                        notificationReceiverHerId = 1000002,
                        notificationTriggeredByHerId = 2000002,
                        description = "New message",
                        createdAt = createdAt,
                    )
                    .toNotification() shouldBe notification
            }

            it("maps every notification type") {
                MshNotificationType.entries.forEach { type ->
                    notification(type).toNotification().type.value shouldBe type.value
                }
            }
        }

        describe("JSON serialization") {
            it("has the field names MSH uses") {
                Json.parseToJsonElement(KxJson.encode(notification, Notification.serializer()))
                    .jsonObject
                    .keys shouldBe
                    setOf(
                        "notificationId",
                        "relatedMessageId",
                        "type",
                        "notificationReceiverHerId",
                        "notificationTriggeredByHerId",
                        "description",
                        "createdAt",
                    )
            }

            it("writes the type as its constant name and the creation time as an ISO offset date-time") {
                val json = KxJson.encode(notification, Notification.serializer())

                json shouldContain """"type":"NEW_MESSAGE""""
                json shouldContain """"createdAt":"2026-10-14T12:00:00+02:00""""
            }

            it("round trips through JSON") {
                KxJson.decode(
                    KxJson.encode(notification, Notification.serializer()),
                    Notification.serializer(),
                ) shouldBe notification
            }

            it("round trips a notification without the optional fields, writing them as null") {
                val refused =
                    Notification(
                        notificationId = "notification-2",
                        relatedMessageId = null,
                        type = NotificationType.REFUSED_MESSAGE,
                        notificationReceiverHerId = 1000002,
                        notificationTriggeredByHerId = null,
                        description = null,
                        createdAt = null,
                    )
                val json = KxJson.encode(refused, Notification.serializer())

                json shouldContain """"relatedMessageId":null"""
                json shouldContain """"createdAt":null"""
                KxJson.decode(json, Notification.serializer()) shouldBe refused
            }

            it("round trips the input to delete notifications") {
                val input = DeleteNotificationsInput(listOf("notification-1", "notification-2"))

                KxJson.decode(
                    KxJson.encode(input, DeleteNotificationsInput.serializer()),
                    DeleteNotificationsInput.serializer(),
                ) shouldBe input
            }
        }
    })

private fun notification(type: MshNotificationType) =
    UnreadNotification(
        notificationId = "notification-1",
        relatedMessageId = null,
        type = type,
        notificationReceiverHerId = 1000002,
        notificationTriggeredByHerId = null,
        description = null,
        createdAt = null,
    )
