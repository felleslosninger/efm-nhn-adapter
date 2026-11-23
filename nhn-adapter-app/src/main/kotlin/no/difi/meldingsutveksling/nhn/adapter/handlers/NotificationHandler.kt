package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.swagger.v3.oas.annotations.Parameter
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.NHNAdapterAuditIdentifier
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.RequestTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MAX_NOTIFICATIONS_TO_DELETE
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MAX_NOTIFICATIONS_TO_FETCH
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.model.DeleteNotificationsInput
import no.difi.meldingsutveksling.nhn.adapter.model.Notification
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toNotification
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

/**
 * Passes MSH's notifications on to the client as they are. A notification stays unread until the client deletes it.
 *
 * [defaultNotificationsToFetch] is how many unread notifications are returned when the client does not ask for a
 * number.
 */
class NotificationHandler(
    private val mshService: MshService,
    private val auditLogService: AuditLogService,
    private val securityService: SecurityService,
    private val defaultNotificationsToFetch: Int,
) {
    init {
        require(defaultNotificationsToFetch in 1..MAX_NOTIFICATIONS_TO_FETCH) {
            "The default number of notifications to fetch must be between 1 and $MAX_NOTIFICATIONS_TO_FETCH, " +
                "but is $defaultNotificationsToFetch"
        }
    }

    suspend fun getUnreadNotifications(
        herId: Int,
        notificationsToFetch: Int?,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val count = notificationsToFetch ?: defaultNotificationsToFetch
        if (count !in 1..MAX_NOTIFICATIONS_TO_FETCH) {
            throw ClientInputException("notificationsToFetch must be between 1 and $MAX_NOTIFICATIONS_TO_FETCH")
        }
        val auditEntry =
            NHNAdapterAuditIdentifier.GET_UNREAD_NOTIFICATIONS.entry(clientContext).attribute("herId", herId)

        // Clients poll this endpoint frequently, so empty results are not audit logged
        var notificationCount = 0
        auditLogService.log(auditEntry, skipOnSuccess = { notificationCount == 0 }) {
            securityService.assertAccessToHerId(clientContext, herId)
            val notifications =
                mshService.getUnreadNotifications(herId, count, clientContext).map { it.toNotification() }
            notificationCount = notifications.size
            auditEntry.attribute("notificationCount", notificationCount)

            val json = jsonParser.encodeToString(ListSerializer(Notification.serializer()), notifications)
            return ServerResponse.ok().json().bodyValueAndAwait(json)
        }
    }

    /** MSH only lets the client delete the notifications of the HER-ids it may act for. */
    suspend fun deleteNotifications(
        request: ServerRequest,
        @Parameter(hidden = true) clientContext: ClientContext,
    ): ServerResponse {
        val auditEntry = NHNAdapterAuditIdentifier.DELETE_NOTIFICATIONS.entry(clientContext)

        auditLogService.log(auditEntry) {
            val notificationIds = readNotificationIds(request)
            auditEntry.attribute("notificationIds", notificationIds)
            mshService.deleteNotifications(notificationIds, clientContext)
            return ServerResponse.ok().textPlain().bodyValueAndAwait("Notifications deleted")
        }
    }

    private suspend fun readNotificationIds(request: ServerRequest): List<String> {
        val input =
            try {
                jsonParser.decodeFromString<DeleteNotificationsInput>(readBody(request))
            } catch (e: IllegalArgumentException) {
                // A SerializationException is an IllegalArgumentException
                throw ClientInputException("Invalid input", e)
            }
        val notificationIds = input.notificationIds.distinct()
        if (notificationIds.isEmpty() || notificationIds.size > MAX_NOTIFICATIONS_TO_DELETE) {
            throw ClientInputException("notificationIds must hold between 1 and $MAX_NOTIFICATIONS_TO_DELETE ids")
        }
        if (notificationIds.any { it.isBlank() || it.length > MAX_NOTIFICATION_ID_LENGTH }) {
            throw ClientInputException("notificationIds must be between 1 and $MAX_NOTIFICATION_ID_LENGTH characters")
        }
        return notificationIds
    }

    /**
     * The body, which is read only up to [MAX_DELETE_REQUEST_BYTES]: the list is small, so a larger body is rejected
     * before it is held in memory, also when it is chunked.
     */
    private suspend fun readBody(request: ServerRequest): String {
        val contentLength = request.headers().contentLength()
        if (contentLength.isPresent && contentLength.asLong > MAX_DELETE_REQUEST_BYTES) {
            throw RequestTooLargeException(MAX_DELETE_REQUEST_BYTES.toLong())
        }
        val body =
            try {
                DataBufferUtils.join(request.body(BodyExtractors.toDataBuffers()), MAX_DELETE_REQUEST_BYTES)
                    .awaitSingleOrNull()
            } catch (_: DataBufferLimitException) {
                throw RequestTooLargeException(MAX_DELETE_REQUEST_BYTES.toLong())
            } ?: throw ClientInputException("Missing input")
        return try {
            body.toString(Charsets.UTF_8)
        } finally {
            DataBufferUtils.release(body)
        }
    }
}

/** Room for [MAX_NOTIFICATIONS_TO_DELETE] ids of [MAX_NOTIFICATION_ID_LENGTH] characters, quoted and separated. */
private const val MAX_DELETE_REQUEST_BYTES = 128 * 1024

/** MSH's notification ids are GUIDs, of 36 characters. */
private const val MAX_NOTIFICATION_ID_LENGTH = 100
